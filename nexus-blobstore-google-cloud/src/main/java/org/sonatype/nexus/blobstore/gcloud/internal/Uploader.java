package org.sonatype.nexus.blobstore.gcloud.internal;

import java.io.InputStream;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;

/**
 * Interface to encapsulate the interactions with {@link Storage} create calls.
 */
public interface Uploader
{
  /**
   *
   * @param storage
   * @param bucket
   * @param destination
   * @param contents
   * @return the google {@link Blob} pointing to the content in the bucket.
   */
  Blob upload(Storage storage, String bucket, String destination, InputStream contents);
}
