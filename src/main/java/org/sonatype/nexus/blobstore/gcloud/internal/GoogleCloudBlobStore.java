package org.sonatype.nexus.blobstore.gcloud.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.BlobSupport;
import org.sonatype.nexus.blobstore.LocationStrategy;
import org.sonatype.nexus.blobstore.MetricsInputStream;
import org.sonatype.nexus.blobstore.StreamMetrics;
import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.blobstore.api.BlobAttributes;
import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobMetrics;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreException;
import org.sonatype.nexus.blobstore.api.BlobStoreMetrics;
import org.sonatype.nexus.blobstore.api.BlobStoreUsageChecker;
import org.sonatype.nexus.common.stateguard.Guarded;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.cache.CacheLoader.from;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.FAILED;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.NEW;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.STARTED;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.STOPPED;

/**
 * Google Cloud Storage backed {@link BlobStore}.
 */
@Named(GoogleCloudBlobStore.TYPE)
public class GoogleCloudBlobStore
    extends StateGuardLifecycleSupport
    implements BlobStore
{
  public static final String TYPE = "Google Cloud Storage";

  public static final String CONFIG_KEY = TYPE.toLowerCase();

  public static final String BUCKET_KEY = "bucket";

  public static final String CREDENTIAL_FILE_KEY = "credential_file";

  public static final String BLOB_CONTENT_SUFFIX = ".bytes";

  public static final String BLOB_ATTRIBUTE_SUFFIX = ".properties";

  public static final String CONTENT_PREFIX = "content";

  public static final String TEMPORARY_BLOB_ID_PREFIX = "tmp$";

  public static final String METADATA_FILENAME = "metadata.properties";

  public static final String TYPE_KEY = "type";

  public static final String TYPE_V1 = "gcp/1";

  private final GoogleCloudStorageFactory storageFactory;

  private final LocationStrategy permanentLocationStrategy;

  private final LocationStrategy temporaryLocationStrategy;

  private final GoogleCloudBlobStoreMetricsStore metricsStore;

  private BlobStoreConfiguration blobStoreConfiguration;

  private Storage storage;

  private Bucket bucket;

  private LoadingCache<BlobId, GoogleCloudStorageBlob> liveBlobs;

  @Inject
  public GoogleCloudBlobStore(final GoogleCloudStorageFactory storageFactory,
                              @Named("volume-chapter") final LocationStrategy permanentLocationStrategy,
                              @Named("temporary") final LocationStrategy temporaryLocationStrategy,
                              final GoogleCloudBlobStoreMetricsStore metricsStore) {
    this.storageFactory = checkNotNull(storageFactory);
    this.permanentLocationStrategy = checkNotNull(permanentLocationStrategy);
    this.temporaryLocationStrategy = checkNotNull(temporaryLocationStrategy);
    this.metricsStore = metricsStore;
  }

  @Override
  protected void doStart() throws Exception {
    this.bucket = getOrCreateStorageBucket();

    GoogleCloudPropertiesFile metadata = new GoogleCloudPropertiesFile(bucket, METADATA_FILENAME);
    if (metadata.exists()) {
      metadata.load();
      String type = metadata.getProperty(TYPE_KEY);
      checkState(TYPE_V1.equals(type), "Unsupported blob store type/version: %s in %s", type, metadata);
    }
    else {
      // assumes new blobstore, write out type
      metadata.setProperty(TYPE_KEY, TYPE_V1);
      metadata.store();
    }
    liveBlobs = CacheBuilder.newBuilder().weakValues().build(from(GoogleCloudStorageBlob::new));

    metricsStore.setBucket(bucket);
    metricsStore.start();
  }

  @Override
  @Guarded(by = STARTED)
  public Blob create(final InputStream inputStream, final Map<String, String> headers) {
    checkNotNull(inputStream);

    return createInternal(headers, destination -> {
      try (InputStream data = inputStream) {
        MetricsInputStream input = new MetricsInputStream(data);
        bucket.create(destination, input);
        return input.getMetrics();
      }
    });
  }

  @Override
  @Guarded(by = STARTED)
  public Blob create(final Path path, final Map<String, String> map, final long size, final HashCode hash) {
    throw new BlobStoreException("hard links not supported", null);
  }

  @Override
  @Guarded(by = STARTED)
  public Blob copy(final BlobId blobId, final Map<String, String> headers) {
    GoogleCloudStorageBlob sourceBlob = checkNotNull(getInternal(blobId));

    return createInternal(headers, destination -> {
      sourceBlob.getBlob().copyTo(getConfiguredBucketName(), destination);
      BlobMetrics metrics = get(blobId).getMetrics();
      return new StreamMetrics(metrics.getContentSize(), metrics.getSha1Hash());
    });
  }

  @Nullable
  @Override
  @Guarded(by = STARTED)
  public Blob get(final BlobId blobId) {
    return getInternal(blobId);
  }

  @Nullable
  @Override
  public Blob get(final BlobId blobId, final boolean includeDeleted) {
    // TODO implement soft-delete
    return getInternal(blobId);
  }

  GoogleCloudStorageBlob getInternal(final BlobId blobId) {
    checkNotNull(blobId);

    final GoogleCloudStorageBlob blob = liveBlobs.getUnchecked(blobId);

    if (blob.isStale()) {
      Lock lock = blob.lock();
      try {
        if (blob.isStale()) {
          GoogleCloudBlobAttributes blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath(blobId).toString());
          boolean loaded = blobAttributes.load();
          if (!loaded) {
            log.warn("Attempt to access non-existent blob {} ({})", blobId, blobAttributes);
            return null;
          }

          if (blobAttributes.isDeleted()) {
            log.warn("Attempt to access soft-deleted blob {} ({})", blobId, blobAttributes);
            return null;
          }

          blob.refresh(blobAttributes.getHeaders(), blobAttributes.getMetrics());
        }
      }
      catch (IOException e) {
        throw new BlobStoreException(e, blobId);
      }
      finally {
        lock.unlock();
      }
    }

    log.debug("Accessing blob {}", blobId);

    return blob;
  }



  @Override
  @Guarded(by = STARTED)
  public boolean delete(final BlobId blobId, final String s) {
    // FIXME: implement soft delete
    return deleteHard(blobId);
  }

  @Override
  @Guarded(by = STARTED)
  public boolean deleteHard(final BlobId blobId) {
    checkNotNull(blobId);

    try {
      log.debug("Hard deleting blob {}", blobId);

      String attributePath = attributePath(blobId);
      GoogleCloudBlobAttributes blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath);
      Long contentSize = getContentSizeForDeletion(blobAttributes);

      GoogleCloudStorageBlob blob = getInternal(blobId);
      boolean blobDeleted = false;
      if (blob != null) {
        blobDeleted = blob.getBlob().delete();
      }

      blobAttributes.setDeleted(blobDeleted);

      if (blobDeleted && contentSize != null) {
        metricsStore.recordDeletion(contentSize);
      }

      return blobDeleted;
    }
    finally {
      liveBlobs.invalidate(blobId);
    }
  }

  @Override
  @Guarded(by = STARTED)
  public BlobStoreMetrics getMetrics() {
    return metricsStore.getMetrics();
  }

  @Override
  @Guarded(by = STARTED)
  public void compact() {
    // no-op
  }

  @Override
  @Guarded(by = STARTED)
  public void compact(@Nullable final BlobStoreUsageChecker blobStoreUsageChecker) {
    // no-op
  }

  @Override
  public BlobStoreConfiguration getBlobStoreConfiguration() {
    return this.blobStoreConfiguration;
  }

  @Override
  public void init(final BlobStoreConfiguration blobStoreConfiguration) throws Exception {
    this.blobStoreConfiguration = blobStoreConfiguration;
    try {
      this.storage = storageFactory.create(blobStoreConfiguration);

      this.bucket = getOrCreateStorageBucket();
    }
    catch (Exception e) {
      throw new BlobStoreException("Unable to initialize blob store bucket: " + getConfiguredBucketName(), e, null);
    }
  }

  protected Bucket getOrCreateStorageBucket() {
    Bucket bucket = storage.get(getConfiguredBucketName());
    if (bucket == null) {
      bucket = storage.create(BucketInfo.of(getConfiguredBucketName()));
    }

    return bucket;
  }

  @Override
  @Guarded(by = {NEW, STOPPED, FAILED})
  public void remove() {
    // TODO delete bucket?
  }

  @Override
  @Guarded(by = STARTED)
  public Stream<BlobId> getBlobIdStream() {
    return Streams.stream(bucket.list(BlobListOption.prefix(CONTENT_PREFIX)).iterateAll())
        .map(blob -> blob.getBlobId())
        .map(blobId -> new BlobId(blobId.toString()));

    /*Iterable<S3ObjectSummary> summaries = S3Objects.withPrefix(s3, getConfiguredBucket(), CONTENT_PREFIX);
    return StreamSupport.stream(summaries.spliterator(), false)
        .map(S3ObjectSummary::getKey)
        .map(key -> key.substring(key.lastIndexOf('/') + 1, key.length()))
        .filter(filename -> filename.endsWith(BLOB_ATTRIBUTE_SUFFIX) && !filename.startsWith(TEMPORARY_BLOB_ID_PREFIX))
        .map(filename -> filename.substring(0, filename.length() - BLOB_ATTRIBUTE_SUFFIX.length()))
        .map(BlobId::new);
        */

  }

  @Override
  @Guarded(by = STARTED)
  public BlobAttributes getBlobAttributes(final BlobId blobId) {
    try {
      GoogleCloudBlobAttributes blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath(blobId));
      return blobAttributes.load() ? blobAttributes : null;
    }
    catch (IOException e) {
      log.error("Unable to load GoogleCloudBlobAttributes for blob id: {}", blobId, e);
      return null;
    }
  }

  Blob createInternal(final Map<String, String> headers, BlobIngester ingester) {
    checkNotNull(headers);

    checkArgument(headers.containsKey(BLOB_NAME_HEADER), "Missing header: %s", BLOB_NAME_HEADER);
    checkArgument(headers.containsKey(CREATED_BY_HEADER), "Missing header: %s", CREATED_BY_HEADER);

    // Generate a new blobId
    BlobId blobId;
    if (headers.containsKey(TEMPORARY_BLOB_HEADER)) {
      blobId = new BlobId(TEMPORARY_BLOB_ID_PREFIX + UUID.randomUUID().toString());
    }
    else {
      blobId = new BlobId(UUID.randomUUID().toString());
    }

    final String blobPath = contentPath(blobId);
    final String attributePath = attributePath(blobId);
    final GoogleCloudStorageBlob blob = liveBlobs.getUnchecked(blobId);
    GoogleCloudBlobAttributes blobAttributes = null;
    Lock lock = blob.lock();
    try {
      log.debug("Writing blob {} to {}", blobId, blobPath);

      final StreamMetrics streamMetrics = ingester.ingestTo(blobPath);
      final BlobMetrics metrics = new BlobMetrics(new DateTime(), streamMetrics.getSha1(), streamMetrics.getSize());
      blob.refresh(headers, metrics);

      blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath, headers, metrics);

      blobAttributes.store();
      metricsStore.recordAddition(blobAttributes.getMetrics().getContentSize());

      return blob;
    }
    catch (IOException e) {
      // TODO delete what we created?
      blob.getBlob().delete();
      if (blobAttributes != null) {
        blobAttributes.setDeleted(true);
      }
      throw new BlobStoreException(e, blobId);
    }
    finally {
      lock.unlock();
    }
  }
  /**
   * Returns path for blob-id content file relative to root directory.
   */
  private String contentPath(final BlobId id) {
    return getLocation(id) + BLOB_CONTENT_SUFFIX;
  }

  /**
   * Returns path for blob-id attribute file relative to root directory.
   */
  private String attributePath(final BlobId id) {
    return getLocation(id) + BLOB_ATTRIBUTE_SUFFIX;
  }

  /**
   * Returns the location for a blob ID based on whether or not the blob ID is for a temporary or permanent blob.
   */
  private String getLocation(final BlobId id) {
    if (id.asUniqueString().startsWith(TEMPORARY_BLOB_ID_PREFIX)) {
      return CONTENT_PREFIX + "/" + temporaryLocationStrategy.location(id);
    }
    return CONTENT_PREFIX + "/" + permanentLocationStrategy.location(id);
  }

  private String getConfiguredBucketName() {
    return blobStoreConfiguration.attributes(CONFIG_KEY).require(BUCKET_KEY).toString();
  }

  private Long getContentSizeForDeletion(final GoogleCloudBlobAttributes blobAttributes) {
    try {
      blobAttributes.load();
      return blobAttributes.getMetrics() != null ? blobAttributes.getMetrics().getContentSize() : null;
    }
    catch (Exception e) {
      log.warn("Unable to load attributes {}, delete will not be added to metrics.", blobAttributes, e);
      return null;
    }
  }

  class GoogleCloudStorageBlob extends BlobSupport {
    GoogleCloudStorageBlob(BlobId blobId) {
      super(blobId);
    }

    @Override
    public InputStream getInputStream() {
      com.google.cloud.storage.Blob blob = getBlob();
      ReadChannel channel = blob.reader();
      return Channels.newInputStream(channel);
    }

    com.google.cloud.storage.Blob getBlob() {
      return bucket.get(contentPath(getId()));
    }
  }

  private interface BlobIngester
  {
    StreamMetrics ingestTo(final String destination) throws IOException;
  }
}
