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

import java.util.stream.StreamSupport

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration

import com.google.cloud.storage.Blob
import com.google.cloud.storage.Blob.BlobSourceOption
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import groovy.util.logging.Slf4j
import org.apache.commons.io.input.BoundedInputStream
import spock.lang.Specification

@Slf4j
class MultipartUploaderIT
    extends Specification
{

  static final BlobStoreConfiguration config = new BlobStoreConfiguration()

  static final GoogleCloudStorageFactory storageFactory = new GoogleCloudStorageFactory()

  static String bucketName = "integration-test-${UUID.randomUUID().toString()}"

  static Storage storage

  def setupSpec() {
    config.attributes = [
        'google cloud storage': [
            bucket: bucketName,
            credential_file: this.getClass().getResource('/gce-credentials.json').getFile()
        ]
    ]

    log.info("Integration test using bucket ${bucketName}")
    storage = storageFactory.create(config)
    storage.create(BucketInfo.of(bucketName))
  }

  def cleanupSpec() {
    //Storage storage = new GoogleCloudStorageFactory().create(config)
    log.debug("Tests complete, deleting files from ${bucketName}")
    // must delete all the files within the bucket before we can delete the bucket
    Iterator<com.google.cloud.storage.Blob> list = storage.list(bucketName,
        Storage.BlobListOption.prefix("")).iterateAll()
        .iterator()

    Iterable<com.google.cloud.storage.Blob> iterable = { _ -> list }
    StreamSupport.stream(iterable.spliterator(), true)
        .forEach({ b -> b.delete(BlobSourceOption.generationMatch()) })
    storage.delete(bucketName)
    log.info("Integration test complete, bucket ${bucketName} deleted")
  }

  def "simple multipart"() {
    given:
      long expectedSize = (1048576 * 3) + 2
      MultipartUploader uploader = new MultipartUploader(1048576)
      byte[] data = new byte[expectedSize]
      new Random().nextBytes(data)

    when:
      Blob blob = uploader.upload(storage, bucketName, 'vol-01/chap-01/control/multi_part', new ByteArrayInputStream(data))

    then:
      blob.size == expectedSize
      storage.get(bucketName, 'vol-01/chap-01/control/multi_part').getContent() == data
  }

  def "confirm parts composed in order"() {
    given:
      // 5 each of abcdefg
      final String content =  "aaaaabbbbbcccccdddddeeeeefffffggggg"
      byte[] data = content.bytes
      MultipartUploader uploader = new MultipartUploader(5)

    when:
      Blob blob = uploader.upload(storage, bucketName, 'vol-01/chap-01/control/in_order', new ByteArrayInputStream(data))

    then:
      blob.size == data.length
      Blob readback = storage.get(blob.blobId)
      readback.getContent() == content.bytes
  }

  def "single part"() {
    given:
      long expectedSize = 1048575
      MultipartUploader uploader = new MultipartUploader(1048576)
      byte[] data = new byte[expectedSize]
      new Random().nextBytes(data)

    when:
      Blob blob = uploader.upload(storage, bucketName, 'vol-01/chap-01/control/single_part', new ByteArrayInputStream(data))

    then:
      blob.size == expectedSize
      storage.get(bucketName, 'vol-01/chap-01/control/single_part').getContent() == data
  }

  def "zero byte file"() {
    given:
      long expectedSize = 0
      MultipartUploader uploader = new MultipartUploader(1024)
      byte[] data = new byte[expectedSize]
      new Random().nextBytes(data)

    when:
      Blob blob = uploader.upload(storage, bucketName, 'vol-01/chap-01/control/zero_byte', new ByteArrayInputStream(data))

    then:
      blob.size == expectedSize
      storage.get(bucketName, 'vol-01/chap-01/control/zero_byte').getContent() == data
  }

  def "hit compose limit slightly and still successful"() {
    given:
      long expectedSize = (1024 * MultipartUploader.COMPOSE_REQUEST_LIMIT) + 10
      MultipartUploader uploader = new MultipartUploader(1024)
      byte[] data = new byte[expectedSize]
      new Random().nextBytes(data)

    when:
      Blob blob = uploader.upload(storage, bucketName,
          'vol-01/chap-01/composeLimitTest/small_miss', new ByteArrayInputStream(data))

    then:
      blob.size == expectedSize
      uploader.numberOfTimesComposeLimitHit == 1L
      storage.get(bucketName, 'vol-01/chap-01/composeLimitTest/small_miss').getContent() == data
  }

  def "hit compose limit poorly tuned, still successful" () {
    given:
      long expectedSize = 1048576
      MultipartUploader uploader = new MultipartUploader(1024)
      byte[] data = new byte[expectedSize]
      new Random().nextBytes(data)

    when:
      Blob blob = uploader.upload(storage, bucketName,
          'vol-01/chap-01/composeLimitTest/poor_tuning', new ByteArrayInputStream(data))

    then:
      blob.size == expectedSize
      uploader.numberOfTimesComposeLimitHit == 1L
      storage.get(bucketName, 'vol-01/chap-01/composeLimitTest/poor_tuning').getContent() == data
  }

  /**
   * Larger upload that still fits ideally within our default tuning. 100 MB will result in 20 5 MB chunks.
   */
  def "upload 100 MB"() {
    given:
      long expectedSize = 1024 * 1024 * 100
      BoundedInputStream inputStream = new BoundedInputStream(new InputStream() {
        private Random random = new Random()
        @Override
        int read() throws IOException {
          return random.nextInt()
        }
      }, expectedSize)
      // default value of 5 MB per chunk
      MultipartUploader uploader = new MultipartUploader(1024 * 1024 * 5)

    when:
      Blob blob = uploader.upload(storage, bucketName,
          'vol-01/chap-02/large/one_hundred_MB', inputStream)
    then:
      blob.size == expectedSize
      storage.get(bucketName, 'vol-01/chap-02/large/one_hundred_MB').size == expectedSize
  }

  /**
   * The difference in this test beyond the 'upload 100 MB' test is that the upload will:
   *
   * a) result in incrementing {@link MultipartUploader#getNumberOfTimesComposeLimitHit()} and
   * b) the last chunk will be significantly larger than the preceding 31.
   *
   * This represents a situation where the customer may need to adjust their chunk size upward. The larger final chunk
   * may also elicit runtime pressure on memory.
   */
  def "upload 300 MB"() {
    given:
      long expectedSize = 1024 * 1024 * 300
      BoundedInputStream inputStream = new BoundedInputStream(new InputStream() {
        private Random random = new Random()
        @Override
        int read() throws IOException {
          return random.nextInt()
        }
      }, expectedSize)
      // with value of 5 MB per chunk, we'll upload 31 5 MB chunks and 1 145 MB chunk
      MultipartUploader uploader = new MultipartUploader(1024 * 1024 * 5)

    when:
      Blob blob = uploader.upload(storage, bucketName,
          'vol-01/chap-02/large/three_hundred_MB', inputStream)
    then:
      blob.size == expectedSize
      uploader.getNumberOfTimesComposeLimitHit() == 1L
      storage.get(bucketName, 'vol-01/chap-02/large/three_hundred_MB').size == expectedSize
  }
}
