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
