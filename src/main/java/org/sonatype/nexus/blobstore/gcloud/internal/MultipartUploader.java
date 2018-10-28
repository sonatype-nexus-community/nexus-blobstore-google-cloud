package org.sonatype.nexus.blobstore.gcloud.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.api.BlobStoreException;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.ComposeRequest;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.io.IOUtils;

/**
 * Component that provides parallel multipart upload support for blob binary data (.bytes files).
 */
@Named
public class MultipartUploader
    extends StateGuardLifecycleSupport
{

  /**
   * Use this property in 'nexus.properties' to control how large each multipart part is. Default is 5 MB.
   * Smaller numbers increase the number of parallel workers used to upload a file. Match to your workload:
   * if you are heavy in docker with large images, increase; if you are heavy in smaller components, decrease.
   */
  public static final String CHUNK_SIZE_PROPERTY = "nexus.gcs.multipartupload.chunksize";

  /**
   * This is a hard limit on the number of components to a compose request enforced by Google Cloud Storage API.
   */
  static final int COMPOSE_REQUEST_LIMIT = 32;

  /**
   * While an invocation of {@link #upload(Storage, String, String, InputStream)} is in-flight, the individual
   * chunks of the file will have names like 'destination.chunkPartNumber", like
   * 'content/vol-01/chap-01/UUID.bytes.chunk1', 'content/vol-01/chap-01/UUID.bytes.chunk2', etc.
   */
  private final String CHUNK_NAME_PART = ".chunk";

  /**
   * Used internally to count how many times we've hit the compose limit.
   * Consider exposing this as a bean that can provide tuning feedback to deployers.
   */
  private final AtomicLong composeLimitHit = new AtomicLong(0);

  private static final byte[] EMPTY = new byte[0];

  private final ListeningExecutorService executorService = MoreExecutors.listeningDecorator(
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setNameFormat("nexus-google-cloud-storage-multipart-upload-%d")
              .build()));

  private final int chunkSize;

  @Inject
  public MultipartUploader(@Named("${"+CHUNK_SIZE_PROPERTY +":-5242880}") final int chunkSize) {
    this.chunkSize = chunkSize;
  }

  @Override
  protected void doStop() throws Exception {
    executorService.shutdown();
    log.info("sent signal to shutdown multipart upload queue, waiting up to 3 minutes for termination...");
    executorService.awaitTermination(3L, TimeUnit.MINUTES);
  }

  /**
   * @return the value for the {@link #CHUNK_SIZE_PROPERTY}
   */
  public int getChunkSize() {
    return chunkSize;
  }

  /**
   * @return the number of times {@link #upload(Storage, String, String, InputStream)} hit the multipart-compose limit
   */
  public long getNumberOfTimesComposeLimitHit() {
    return composeLimitHit.get();
  }

  /**
   * @param storage an initialized {@link Storage} instance
   * @param bucket the name of the bucket
   * @param destination the the destination (relative to the bucket)
   * @param contents the stream of data to store
   * @return the successfully stored {@link Blob}
   * @throws BlobStoreException if any part of the upload failed
   */
  public Blob upload(final Storage storage, final String bucket, final String destination, final InputStream contents) {
    log.debug("Starting multipart upload for destination {} in bucket {}", destination, bucket);
    // collect parts as blobids in a list?
    List<String> parts = new ArrayList<>();

    try (InputStream current = contents) {
      List<ListenableFuture<Blob>> chunkFutures = new ArrayList<>();
      // MUST respect hard limit of 32 chunks per compose request
      for (int partNumber = 1; partNumber <= COMPOSE_REQUEST_LIMIT; partNumber++) {
        final byte[] chunk;
        if (partNumber < COMPOSE_REQUEST_LIMIT) {
          chunk = readChunk(current);
        }
        else {
          log.debug("Upload for {} has hit Google Cloud Storage multipart-compose limits; " +
              "consider increasing '{}' beyond current value of {}", destination, CHUNK_SIZE_PROPERTY, getChunkSize());
          // we've hit compose request limit read the rest of the stream
          composeLimitHit.incrementAndGet();
          chunk = IOUtils.toByteArray(current);
        }
        if (chunk == EMPTY && partNumber > 1) {
          break;
        }
        else {
          final int chunkIndex = partNumber;
          chunkFutures.add(executorService.submit(() -> {
            log.debug("Uploading chunk {} for {} of {} bytes", chunkIndex, destination, chunk.length);
            BlobInfo blobInfo = BlobInfo.newBuilder(
                bucket, toChunkName(destination, chunkIndex)).build();
            return storage.create(blobInfo, chunk);
          }));
        }
      }

      final int numberOfChunks = chunkFutures.size();
      CountDownLatch block = new CountDownLatch(1);
      Futures.whenAllComplete(chunkFutures).run(() -> block.countDown() , MoreExecutors.directExecutor());
      // create list of all the chunks
      List<String> chunkBlobs = IntStream.rangeClosed(1, numberOfChunks)
          .mapToObj(i -> toChunkName(destination, i))
          .collect(Collectors.toList());

      // wait for all the futures to complete
      log.debug("waiting for {} chunks to complete", chunkFutures.size());
      block.await();
      log.debug("chunk uploads completed, sending compose request");
      // finalize with compose request to coalesce the chunks
      Blob finalBlob = storage.compose(ComposeRequest.of(bucket, chunkBlobs, destination));
      log.debug("Multipart upload of {} complete", destination);

      deferredCleanup(storage, bucket, chunkBlobs);

      return finalBlob;
    }
    catch(Exception e) {
      deferredCleanup(storage, bucket, parts);
      throw new BlobStoreException("Error uploading blob", e, null);
    }
  }

  private void deferredCleanup(final Storage storage, final String bucket, final List<String> parts) {
    executorService.submit(() -> {
      parts.stream().forEach(part -> storage.delete(bucket, part));
    });
  }

  private String toChunkName(String destination, int chunkNumber) {
    return destination + CHUNK_NAME_PART + chunkNumber;
  }

  /**
   * Read a chunk of the stream up to {@link #getChunkSize()} in length.
   *
   * @param input
   * @return the read data as a byte array
   * @throws IOException
   */
  private byte[] readChunk(final InputStream input) throws IOException {
    byte[] buffer = new byte[chunkSize];
    int offset = 0;
    int remain = chunkSize;
    int bytesRead = 0;

    while (remain > 0 && bytesRead >= 0) {
      bytesRead = input.read(buffer, offset, remain);
      if (bytesRead > 0) {
        offset += bytesRead;
        remain -= bytesRead;
      }
    }
    if (offset > 0) {
      return Arrays.copyOfRange(buffer, 0, offset);
    }
    else {
      return EMPTY;
    }
  }
}
