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
import java.nio.channels.Channels;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.BlobIdLocationResolver;
import org.sonatype.nexus.blobstore.BlobSupport;
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
import org.sonatype.nexus.logging.task.ProgressLogIntervalHelper;
import org.sonatype.nexus.scheduling.CancelableHelper;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobField;
import com.google.cloud.storage.Storage.BlobGetOption;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.cache.CacheLoader.from;
import static java.lang.String.format;
import static org.sonatype.nexus.blobstore.DirectPathLocationStrategy.DIRECT_PATH_ROOT;
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

  static final String CONTENT_PREFIX = "content";

  public static final String TEMPORARY_BLOB_ID_PREFIX = "tmp$";

  public static final String METADATA_FILENAME = "metadata.properties";

  public static final String TYPE_KEY = "type";

  public static final String TYPE_V1 = "gcp/1";

  private static final String FILE_V1 = "file/1";

  private final GoogleCloudStorageFactory storageFactory;

  private final BlobIdLocationResolver blobIdLocationResolver;

  private final GoogleCloudBlobStoreMetricsStore metricsStore;

  private BlobStoreConfiguration blobStoreConfiguration;

  private Storage storage;

  private Bucket bucket;

  private GoogleCloudDatastoreFactory datastoreFactory;

  private DeletedBlobIndex deletedBlobIndex;

  private LoadingCache<BlobId, GoogleCloudStorageBlob> liveBlobs;

  @Inject
  public GoogleCloudBlobStore(final GoogleCloudStorageFactory storageFactory,
                              final BlobIdLocationResolver blobIdLocationResolver,
                              final GoogleCloudBlobStoreMetricsStore metricsStore,
                              final GoogleCloudDatastoreFactory datastoreFactory) {
    this.storageFactory = checkNotNull(storageFactory);
    this.blobIdLocationResolver = checkNotNull(blobIdLocationResolver);
    this.metricsStore = metricsStore;
    this.datastoreFactory = datastoreFactory;
  }

  @Override
  protected void doStart() throws Exception {
    GoogleCloudPropertiesFile metadata = new GoogleCloudPropertiesFile(bucket, METADATA_FILENAME);
    if (metadata.exists()) {
      metadata.load();
      String type = metadata.getProperty(TYPE_KEY);
      checkState(TYPE_V1.equals(type) || FILE_V1.equals(type),
          "Unsupported blob store type/version: %s in %s", type, metadata);
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
  protected void doStop() throws Exception {
    liveBlobs = null;
    metricsStore.stop();
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
    GoogleCloudStorageBlob sourceBlob = (GoogleCloudStorageBlob) checkNotNull(get(blobId));

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
    return get(blobId, false);
  }

  @Nullable
  @Override
  public Blob get(final BlobId blobId, final boolean includeDeleted) {
    checkNotNull(blobId);

    final GoogleCloudStorageBlob blob = liveBlobs.getUnchecked(blobId);

    if (blob.isStale()) {
      Lock lock = blob.lock();
      try {
        if (blob.isStale()) {
          GoogleCloudBlobAttributes blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath(blobId));
          boolean loaded = blobAttributes.load();
          if (!loaded) {
            log.warn("Attempt to access non-existent blob {} ({})", blobId, blobAttributes);
            return null;
          }

          if (blobAttributes.isDeleted() && !includeDeleted) {
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
  public boolean delete(final BlobId blobId, final String reason) {
    checkNotNull(blobId);

    final GoogleCloudStorageBlob blob = liveBlobs.getUnchecked(blobId);

    Lock lock = blob.lock();
    try {
      log.debug("Soft deleting blob {}", blobId);

      GoogleCloudBlobAttributes blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath(blobId));

      boolean loaded = blobAttributes.load();
      if (!loaded) {
        log.warn("Attempt to mark-for-delete non-existent blob {}", blobId);
        return false;
      }
      else if (blobAttributes.isDeleted()) {
        log.debug("Attempt to delete already-deleted blob {}", blobId);
        return false;
      }

      blobAttributes.setDeleted(true);
      blobAttributes.setDeletedReason(reason);
      blobAttributes.store();

      // add the blobId to the soft-deleted index
      deletedBlobIndex.add(blobId);
      blob.markStale();

      return true;
    }
    catch (Exception e) {
      throw new BlobStoreException(e, blobId);
    }
    finally {
      lock.unlock();
    }
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

      boolean blobDeleted = storage.delete(getConfiguredBucketName(), contentPath(blobId));
      if (blobDeleted) {
        storage.delete(getConfiguredBucketName(), attributePath);
        deletedBlobIndex.remove(blobId);
      }

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
    compact(null);
  }

  @Override
  @Guarded(by = STARTED)
  public void compact(@Nullable final BlobStoreUsageChecker blobStoreUsageChecker) {

    log.info("Begin deleted blobs processing");
    ProgressLogIntervalHelper progressLogger = new ProgressLogIntervalHelper(log, 60);
    final AtomicInteger counter = new AtomicInteger(0);
    deletedBlobIndex.getContents().forEach(blobId -> {
      CancelableHelper.checkCancellation();

      deleteHard(blobId);
      counter.incrementAndGet();

      progressLogger.info("Elapsed time: {}, processed: {}", progressLogger.getElapsed(),
          counter.get());
    });
    progressLogger.flush();
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

      this.deletedBlobIndex = new DeletedBlobIndex(this.datastoreFactory, blobStoreConfiguration);
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
    // TODO delete bucket only if it is empty
  }

  @Override
  @Guarded(by = STARTED)
  public Stream<BlobId> getBlobIdStream() {
    return blobStream(CONTENT_PREFIX)
        .filter(blob -> blob.getName().endsWith(BLOB_ATTRIBUTE_SUFFIX) &&
            !basename(blob).startsWith(TEMPORARY_BLOB_ID_PREFIX))
        .map(com.google.cloud.storage.Blob::getBlobId)
        .map(blobId -> new BlobId(blobId.toString()));
  }

  @Override
  @Guarded(by = STARTED)
  public Stream<BlobId> getDirectPathBlobIdStream(final String prefix) {
    String subpath = format("%s/%s/%s", CONTENT_PREFIX, DIRECT_PATH_ROOT, prefix);
    return blobStream(subpath)
        .filter(blob -> blob.getName().endsWith(BLOB_ATTRIBUTE_SUFFIX) &&
            !basename(blob).startsWith(TEMPORARY_BLOB_ID_PREFIX))
        .map(blob -> cloudBlobIdToDirectPathBlobId(blob.getBlobId()));
  }

  /**
   * Used by {@link #getDirectPathBlobIdStream(String)} to convert an Google cloud BlobId to a Nexus {@link BlobId}.
   *
   * @see BlobIdLocationResolver
   */
  private BlobId cloudBlobIdToDirectPathBlobId(final com.google.cloud.storage.BlobId blobId) {
    final String blobName = blobId.getName();
    checkArgument(blobName.startsWith(CONTENT_PREFIX + "/" + DIRECT_PATH_ROOT + "/"),
        "Not direct path blob path: %s", blobName);
    checkArgument(blobName.endsWith(BLOB_ATTRIBUTE_SUFFIX), "Not blob attribute path: %s", blobName);
    String subpath = blobName.replace(format("%s/%s/", CONTENT_PREFIX, DIRECT_PATH_ROOT), "");
    String name = subpath.substring(0, subpath.length() - BLOB_ATTRIBUTE_SUFFIX.length());

    Map<String, String> headers = ImmutableMap.of(
        BLOB_NAME_HEADER, name,
        DIRECT_PATH_BLOB_HEADER, "true"
    );
    return blobIdLocationResolver.fromHeaders(headers);
  }

  Stream<com.google.cloud.storage.Blob> blobStream(final String path) {
    return Streams.stream(bucket.list(BlobListOption.prefix(path)).iterateAll());
  }

  String basename(final com.google.cloud.storage.Blob blob) {
    String name = blob.getName();
    return name.substring(name.lastIndexOf('/') + 1);
  }

  /**
   * @return the {@link BlobAttributes} for the blod, or null
   * @throws BlobStoreException if an {@link IOException} occurs
   */
  @Override
  @Guarded(by = STARTED)
  public BlobAttributes getBlobAttributes(final BlobId blobId) {
    try {
      GoogleCloudBlobAttributes blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath(blobId));
      return blobAttributes.load() ? blobAttributes : null;
    }
    catch (IOException e) {
      log.error("Unable to load GoogleCloudBlobAttributes for blob id: {}", blobId, e);
      throw new BlobStoreException(e, blobId);
    }
  }

  @Override
  @Guarded(by = STARTED)
  public void setBlobAttributes(final BlobId blobId, final BlobAttributes blobAttributes) {
    GoogleCloudBlobAttributes existing = (GoogleCloudBlobAttributes) getBlobAttributes(blobId);
    if (existing != null) {
      try {
        existing.updateFrom(blobAttributes);
        existing.store();
      } catch (IOException e) {
        log.error("Unable to set GoogleCloudBlobAttributes for blob id: {}", blobId, e);
      }
    }
  }

  /**
   * @return true if a blob exists in the store with the provided {@link BlobId}
   * @throws BlobStoreException if an IOException occurs
   */
  @Override
  public boolean exists(final BlobId blobId) {
    checkNotNull(blobId);
    return getBlobAttributes(blobId) != null;
  }

  Blob createInternal(final Map<String, String> headers, BlobIngester ingester) {
    checkNotNull(headers);

    checkArgument(headers.containsKey(BLOB_NAME_HEADER), "Missing header: %s", BLOB_NAME_HEADER);
    checkArgument(headers.containsKey(CREATED_BY_HEADER), "Missing header: %s", CREATED_BY_HEADER);

    final BlobId blobId = blobIdLocationResolver.fromHeaders(headers);

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
      deleteNonExplosively(attributePath);
      deleteNonExplosively(blobPath);
      throw new BlobStoreException(e, blobId);
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Intended for use only within catch blocks that intend to throw their own {@link BlobStoreException}
   * for another good reason.
   *
   * @param contentPath the path within the configured bucket to delete
   */
  private void deleteNonExplosively(final String contentPath) {
    try {
      storage.delete(getConfiguredBucketName(), contentPath);
    } catch (Exception e) {
      log.warn("caught exception attempting to delete during cleanup", e);
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
    return CONTENT_PREFIX + "/" + blobIdLocationResolver.getLocation(id);
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
      return bucket.get(contentPath(getId()), BlobGetOption.fields(BlobField.MEDIA_LINK));
    }
  }

  private interface BlobIngester
  {
    StreamMetrics ingestTo(final String destination) throws IOException;
  }
}
