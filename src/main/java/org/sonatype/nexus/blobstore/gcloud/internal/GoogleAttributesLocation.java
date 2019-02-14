package org.sonatype.nexus.blobstore.gcloud.internal;

import org.sonatype.nexus.blobstore.AttributesLocation;

import com.google.cloud.storage.BlobInfo;

import static com.google.common.base.Preconditions.checkNotNull;

public class GoogleAttributesLocation
    implements AttributesLocation
{
  private String key;

  public GoogleAttributesLocation(final BlobInfo blobInfo) {
    checkNotNull(blobInfo);
    this.key = checkNotNull(blobInfo.getName());
  }

  @Override
  public String getFileName() {
    return key.substring(key.lastIndexOf('/') + 1);
  }

  @Override
  public String getFullPath() {
    return key;
  }

}
