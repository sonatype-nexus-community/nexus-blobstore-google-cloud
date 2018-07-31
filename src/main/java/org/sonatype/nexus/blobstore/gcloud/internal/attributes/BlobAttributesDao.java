package org.sonatype.nexus.blobstore.gcloud.internal.attributes;

import java.util.Map;

import javax.annotation.Nullable;

import org.sonatype.nexus.blobstore.api.BlobAttributes;
import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobMetrics;

/**
 * Used internally by {@link org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore} to encapsulate
 * maintenance of {@link BlobAttributes}.
 */
public interface BlobAttributesDao
{
  @Nullable
  BlobAttributes getAttributes(BlobId blobId);

  void deleteAttributes(BlobId blobId);

  void markDeleted(BlobId blobId, String reason);

  void undelete(BlobId blobId);

  BlobAttributes storeAttributes(BlobId blobId, BlobAttributes blobAttributes);

  BlobAttributes storeAttributes(BlobId blobId, Map<String, String> headers, BlobMetrics metrics);
}
