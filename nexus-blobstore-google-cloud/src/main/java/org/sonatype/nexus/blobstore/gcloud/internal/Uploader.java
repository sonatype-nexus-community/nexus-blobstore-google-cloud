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
