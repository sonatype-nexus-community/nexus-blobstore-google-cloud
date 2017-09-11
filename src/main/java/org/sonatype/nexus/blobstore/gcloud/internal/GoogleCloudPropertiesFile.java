package org.sonatype.nexus.blobstore.gcloud.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Properties;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link Properties} representation stored in Google Cloud Storage.
 */
public class GoogleCloudPropertiesFile
    extends Properties
{
  private static final Logger log = LoggerFactory.getLogger(GoogleCloudPropertiesFile.class);

  private final Bucket bucket;

  private final String key;

  public GoogleCloudPropertiesFile(final Bucket bucket, final String key) {
    this.bucket = checkNotNull(bucket);
    this.key = checkNotNull(key);
  }

  public void load() throws IOException {
    log.debug("Loading properties: {}", key);

    Blob blob = bucket.get(key);
    try (ReadChannel channel = blob.reader()) {
      load(Channels.newInputStream(channel));
    }
  }

  public void store() throws IOException {
    log.debug("Storing properties: {}", key);

    // write this properties instance to an in-memory buffer
    ByteArrayOutputStream bufferStream = new ByteArrayOutputStream();
    store(bufferStream, null);
    byte[] buffer = bufferStream.toByteArray();

    // upload the buffer to the bucket
    bucket.create(key, buffer);
  }

  public boolean exists() throws IOException {
    return bucket.get(key) != null;
  }

  public void remove() throws IOException {
    Blob blob = bucket.get(key);
    if (blob != null) {
      blob.delete();
    }
  }

  public String toString() {
    return getClass().getSimpleName() + "{" +
        "key=" + key +
        '}';
  }
}
