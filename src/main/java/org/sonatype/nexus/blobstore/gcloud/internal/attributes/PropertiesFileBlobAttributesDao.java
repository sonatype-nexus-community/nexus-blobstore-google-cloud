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
package org.sonatype.nexus.blobstore.gcloud.internal.attributes;

import java.io.IOException;
import java.util.Map;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.blobstore.BlobIdLocationResolver;
import org.sonatype.nexus.blobstore.api.BlobAttributes;
import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobMetrics;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreException;
import org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobAttributes;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;

import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.CONTENT_PREFIX;

/**
 * {@link BlobAttributesDao} backed by {@link org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudPropertiesFile}s
 * in the Google Cloud Storage bucket.
 */
class PropertiesFileBlobAttributesDao
    extends ComponentSupport
    implements BlobAttributesDao
{
  public static final String BLOB_ATTRIBUTE_SUFFIX = ".properties";

  private BlobIdLocationResolver blobIdLocationResolver;

  private BlobStoreConfiguration blobStoreConfiguration;

  private final Bucket bucket;

  private final String bucketName;

  private final Storage storage;

  public PropertiesFileBlobAttributesDao(final BlobIdLocationResolver blobIdLocationResolver,
                                         final Bucket bucket,
                                         final Storage storage,
                                         final BlobStoreConfiguration blobStoreConfiguration,
                                         final String bucketName) {
    this.blobIdLocationResolver = blobIdLocationResolver;
    this.bucket = bucket;
    this.storage = storage;

    this.blobStoreConfiguration = blobStoreConfiguration;
    this.bucketName = bucketName;
  }

  @Override
  public GoogleCloudBlobAttributes getAttributes(BlobId blobId) {
    GoogleCloudBlobAttributes blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath(blobId));
    try {
      boolean loaded = blobAttributes.load();
      if (!loaded) {
        log.warn("Attempt to access non-existent blob {} ({})", blobId, blobAttributes);
        return null;
      }

      return blobAttributes;
    } catch (IOException e) {
      log.error("Unable to load GoogleCloudBlobAttributes for blob id: {}", blobId, e);
      throw new BlobStoreException(e, blobId);
    }
  }

  @Override
  public void deleteAttributes(BlobId blobId) {
    storage.delete(bucketName, attributePath(blobId));
  }

  @Override
  public void markDeleted(BlobId blobId, String reason) {
    GoogleCloudBlobAttributes blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath(blobId));

    try {
      boolean loaded = blobAttributes.load();
      if (!loaded) {
        log.warn("Attempt to mark-for-delete non-existent blob {}", blobId);
      }
      else if (blobAttributes.isDeleted()) {
        log.debug("Attempt to delete already-deleted blob {}", blobId);
      }

      blobAttributes.setDeleted(true);
      blobAttributes.setDeletedReason(reason);
      blobAttributes.store();
    } catch (IOException e) {
      throw new BlobStoreException(e, blobId);
    }
  }

  @Override
  public void undelete(BlobId blobId) {
    GoogleCloudBlobAttributes blobAttributes = new GoogleCloudBlobAttributes(bucket, attributePath(blobId));

    try {
      boolean loaded = blobAttributes.load();
      if (!loaded) {
        log.warn("Attempt to undelete non-existent blob {}", blobId);
      }

      blobAttributes.setDeleted(false);
      blobAttributes.setDeletedReason(null);
      blobAttributes.store();
    } catch (IOException e) {
      throw new BlobStoreException(e, blobId);
    }
  }

  @Override
  public BlobAttributes storeAttributes(BlobId blobId, BlobAttributes blobAttributes) {
    return storeAttributes(blobId, blobAttributes.getHeaders(), blobAttributes.getMetrics());
  }

  @Override
  public BlobAttributes storeAttributes(final BlobId blobId,
                                        final Map<String, String> headers,
                                        final BlobMetrics metrics)
  {
    final String attributePath = attributePath(blobId);
    GoogleCloudBlobAttributes attributes = new GoogleCloudBlobAttributes(bucket, attributePath,
        headers, metrics);

    try {
      attributes.store();
    }
    catch (IOException e) {
      deleteNonExplosively(attributePath);
      throw new BlobStoreException(e, blobId);
    }

    return attributes;
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

  /**
   * Intended for use only within catch blocks that intend to throw their own {@link BlobStoreException}
   * for another good reason.
   *
   * @param contentPath the path within the configured bucket to delete
   */
  private void deleteNonExplosively(final String contentPath) {
    try {
      storage.delete(bucketName, contentPath);
    } catch (Exception e) {
      log.warn("caught exception attempting to delete during cleanup", e);
    }
  }
}
