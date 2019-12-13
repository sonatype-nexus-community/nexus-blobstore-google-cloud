package org.sonatype.nexus.blobstore.gcloud.internal;

import java.io.InputStream;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;

public interface Uploader
{
  Blob upload(Storage storage, String bucket, String destination, InputStream contents);
}
