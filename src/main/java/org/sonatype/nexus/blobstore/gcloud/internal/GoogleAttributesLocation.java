package org.sonatype.nexus.blobstore.gcloud.internal;

import org.sonatype.nexus.blobstore.AttributesLocation;

import com.google.cloud.storage.BlobInfo;

import static com.google.common.base.Preconditions.checkNotNull;

public class GoogleAttributesLocation
    implements AttributesLocation
{
  private final String key;
  private final String fullPath;

  public GoogleAttributesLocation(final BlobInfo blobInfo) {
    checkNotNull(blobInfo);
    this.key = checkNotNull(blobInfo.getName());
    this.fullPath = key.substring(key.lastIndexOf('/') + 1);
  }

  @Override
  public String getFileName() {
    return this.fullPath;
  }

  @Override
  public String getFullPath() {
    return key;
  }

}
