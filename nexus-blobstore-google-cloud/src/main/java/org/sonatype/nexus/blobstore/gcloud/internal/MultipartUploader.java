/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2017-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.blobstore.gcloud.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.blobstore.api.BlobStoreException;
import org.sonatype.nexus.common.app.ManagedLifecycle;
import org.sonatype.nexus.common.app.ManagedLifecycle.Phase;
import org.sonatype.nexus.common.stateguard.Guarded;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;
import org.sonatype.nexus.thread.NexusThreadFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.MetricRegistry;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobTargetOption;
import com.google.cloud.storage.Storage.BlobWriteOption;
import com.google.cloud.storage.Storage.ComposeRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.String.format;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.STARTED;

/**
 * Component that provides optional parallel multipart upload support for blob binary data (.bytes files).
 */
@Named
@ManagedLifecycle(phase = Phase.STORAGE)
@Singleton
public class MultipartUploader
    extends StateGuardLifecycleSupport
    implements Uploader
{

  /**
   * Use this property in 'nexus.properties' to control how large each multipart part is. Default is 2 MB.
   * Smaller numbers increase the number of parallel workers used to upload a file.
   * Inspect the '/service/metrics/data' endpoint, specifically the
   * <pre>.histograms["org.sonatype.nexus.blobstore.gcloud.internal.MultipartUploader.chunks"]</pre> field.
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

  private static final byte[] EMPTY = new byte[0];

  private final ListeningExecutorService executorService;

  private final int chunkSize;

  private final Histogram numberOfChunks;

  private final Counter composeLimitHitCounter;

  @Inject
  public MultipartUploader(final MetricRegistry metricRegistry,
                           @Named("${"+CHUNK_SIZE_PROPERTY +":-0}") final int chunkSize) {
    checkArgument(chunkSize >= 0, CHUNK_SIZE_PROPERTY + " cannot be negative");
    this.chunkSize = chunkSize;
    this.executorService = MoreExecutors.listeningDecorator(
        new InstrumentedExecutorService(
          Executors.newCachedThreadPool(
            new NexusThreadFactory("multipart-upload", "nexus-blobstore-google-cloud")),
          metricRegistry, format("%s.%s", MultipartUploader.class.getName(), "executor-service")));
    this.numberOfChunks = metricRegistry.histogram(MetricRegistry.name(MultipartUploader.class, "chunks"));
    this.composeLimitHitCounter = metricRegistry.counter(MetricRegistry.name(MultipartUploader.class, "composeLimitHits"));
  }

  @Override
  protected void doStart() {
    if(isParallel()) {
      log.info("parallel upload to Google Cloud Storage enabled with buffer size {}", getChunkSize());
    }
  }
  @Override
  protected void doStop() {
    executorService.shutdownNow();
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
    return composeLimitHitCounter.getCount();
  }

  public boolean isParallel() {
    return getChunkSize() > 0;
  }

  /**
   * @param storage an initialized {@link Storage} instance
   * @param bucket the name of the bucket
   * @param destination the destination (relative to the bucket)
   * @param contents the stream of data to store
   * @return the successfully stored {@link Blob}
   * @throws BlobStoreException if any part of the upload failed
   */
  @Override
  @Guarded(by = STARTED)
  public Blob upload(final Storage storage, final String bucket, final String destination, final InputStream contents) {
    if(isParallel()) {
      return parallelUpload(storage, bucket, destination, contents);
    }
    log.debug("Starting upload for destination {} in bucket {}", destination, bucket);
    BlobInfo blobInfo = BlobInfo.newBuilder(bucket, destination).build();
    Blob result = storage.create(blobInfo, contents, BlobWriteOption.disableGzipContent());
    log.debug("Upload of {} complete", destination);
    return result;
  }

  /**
   * @param storage an initialized {@link Storage} instance
   * @param bucket the name of the bucket
   * @param destination the destination (relative to the bucket)
   * @param contents the stream of data to store
   * @return the successfully stored {@link Blob}
   * @throws BlobStoreException if any part of the upload failed
   */
  Blob parallelUpload(final Storage storage, final String bucket, final String destination, final InputStream contents) {
    log.debug("Starting parallel multipart upload for destination {} in bucket {}", destination, bucket);
    // this must represent the bucket-relative paths to the chunks, in order of composition
    List<String> chunkNames = new ArrayList<>();

    Optional<Blob> singleChunk = Optional.empty();
    try (InputStream current = contents) {
      List<ListenableFuture<Blob>> chunkFutures = new ArrayList<>();
      // MUST respect hard limit of 32 chunks per compose request
      for (int partNumber = 1; partNumber <= COMPOSE_REQUEST_LIMIT; partNumber++) {
        final byte[] chunk;
        if (partNumber < COMPOSE_REQUEST_LIMIT) {
          chunk = readChunk(current);
        }
        else {
          // we've hit compose request limit
          composeLimitHitCounter.inc();
          chunk = EMPTY;
          log.debug("Upload for {} has hit Google Cloud Storage multipart-compose limit ({} total times limit hit)",
              destination, getNumberOfTimesComposeLimitHit());

          final String finalChunkName = toChunkName(destination, COMPOSE_REQUEST_LIMIT);
          chunkNames.add(finalChunkName);
          chunkFutures.add(executorService.submit(() -> {
            log.debug("Uploading final chunk {} for {} of unknown remaining bytes", COMPOSE_REQUEST_LIMIT, destination);
            BlobInfo blobInfo = BlobInfo.newBuilder(bucket, finalChunkName).build();
            // read the rest of the current stream
            // downside here is that since we don't know the stream size, we can't chunk it.
            return storage.create(blobInfo, current, BlobWriteOption.disableGzipContent());
          }));
        }

        if (chunk == EMPTY && partNumber > 1) {
          break;
        }

        final String chunkName = toChunkName(destination, partNumber);
        chunkNames.add(chunkName);

        if (partNumber == 1) {
          // upload the first part on the current thread
          BlobInfo blobInfo = BlobInfo.newBuilder(bucket, chunkName).build();
          Blob blob = storage.create(blobInfo, chunk, BlobTargetOption.disableGzipContent());
          singleChunk = Optional.of(blob);
        }
        else {
          singleChunk = Optional.empty();
          // 2nd through N chunks will happen off current thread in parallel
          final int chunkIndex = partNumber;
          chunkFutures.add(executorService.submit(() -> {
            log.debug("Uploading chunk {} for {} of {} bytes", chunkIndex, destination, chunk.length);
            BlobInfo blobInfo = BlobInfo.newBuilder(
                bucket, chunkName).build();
            return storage.create(blobInfo, chunk, BlobTargetOption.disableGzipContent());
          }));
        }
      }

      // return the single result if it exists; otherwise finalize the parallel multipart workers
      return singleChunk.orElseGet(() -> {
        CountDownLatch block = new CountDownLatch(1);
        Futures.whenAllComplete(chunkFutures).run(() -> block.countDown() , MoreExecutors.directExecutor());
        // wait for all the futures to complete
        log.debug("waiting for {} remaining chunks to complete", chunkFutures.size());
        try {
          block.await();
        }
        catch (InterruptedException e) {
          log.error("caught InterruptedException waiting for multipart upload to complete on {}", destination);
          throw new RuntimeException(e);
        }
        log.debug("chunk uploads completed, sending compose request");

        // finalize with compose request to coalesce the chunks
        Blob finalBlob = storage.compose(ComposeRequest.of(bucket, chunkNames, destination));
        log.debug("Parallel multipart upload of {} complete", destination);
        return finalBlob;
      });
    }
    catch(Exception e) {
      throw new BlobStoreException("Error uploading blob", e, null);
    }
    finally {
      numberOfChunks.update(chunkNames.size());
      // remove any .chunkN files off-thread
      // make sure not to delete the first chunk (which has the desired destination name with no suffix)
      deferredCleanup(storage, bucket, chunkNames);
    }
  }

  @VisibleForTesting
  Histogram numberOfChunksHistogram() {
    return this.numberOfChunks;
  }

  private void deferredCleanup(final Storage storage, final String bucket, final List<String> chunkNames) {
    executorService.submit(() -> chunkNames.stream()
        .filter(part -> part.contains(CHUNK_NAME_PART))
        .forEach(chunk -> storage.delete(bucket, chunk)));
  }

  /**
   * The name of the first chunk should match the desired end destination.
   * For any chunk index 2 or greater, this method will return the destination + the chunk name suffix.
   *
   * @param destination
   * @param chunkNumber
   * @return the name to store this chunk
   */
  private String toChunkName(String destination, int chunkNumber) {
    if (chunkNumber == 1) {
      return destination;
    }
    return destination + CHUNK_NAME_PART + chunkNumber;
  }

  /**
   * Read a chunk of the stream up to {@link #getChunkSize()} in length.
   *
   * @param input the stream to read
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
