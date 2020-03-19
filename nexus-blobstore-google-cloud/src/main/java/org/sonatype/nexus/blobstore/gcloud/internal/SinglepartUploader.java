package org.sonatype.nexus.blobstore.gcloud.internal;

import java.io.InputStream;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobWriteOption;

/**
 * Simplest form of {@link Uploader}, passes through to
 * {@link Storage#create(BlobInfo, InputStream, BlobWriteOption...)} on the current thread.
 */
public class SinglepartUploader
    implements Uploader
{

  @Override
  public Blob upload(final Storage storage, final String bucket, final String destination, final InputStream contents) {
    BlobInfo blobInfo = BlobInfo.newBuilder(bucket, destination).build();
    return storage.create(blobInfo, contents, BlobWriteOption.disableGzipContent());
  }
}
