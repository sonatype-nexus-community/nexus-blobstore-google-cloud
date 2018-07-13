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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.Properties;

import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.BlobField;
import com.google.cloud.storage.Storage.BlobGetOption;
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

    Blob blob = bucket.get(key, BlobGetOption.fields(BlobField.MEDIA_LINK));
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
