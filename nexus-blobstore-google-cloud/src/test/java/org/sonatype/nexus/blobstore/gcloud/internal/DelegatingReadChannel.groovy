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
package org.sonatype.nexus.blobstore.gcloud.internal

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

import com.google.cloud.ReadChannel
import com.google.cloud.RestorableState

/**
 * Custom {@link ReadChannel} implementation useful for testing; delegates to a {@link FileChannel} so that
 * we can simulate content returned from a Google {@link com.google.cloud.storage.Blob}.
 */
class DelegatingReadChannel
    implements ReadChannel
{
  private final FileChannel delegate

  DelegatingReadChannel(final FileChannel delegate) {
    this.delegate = delegate
  }

  @Override
  boolean isOpen() {
    return delegate.isOpen()
  }

  @Override
  void close() {
    delegate.close()
  }

  @Override
  void seek(final long position) throws IOException {
    //ignored
  }

  @Override
  void setChunkSize(final int chunkSize) {
    //ignored
  }

  @Override
  RestorableState<ReadChannel> capture() {
    return null
  }

  @Override
  int read(final ByteBuffer dst) throws IOException {
    return delegate.read(dst)
  }
}
