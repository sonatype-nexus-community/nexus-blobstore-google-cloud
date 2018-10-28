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

  def "control experiment"() {
    given:
      long expectedSize = (1048576 * 3) + 2
      MultipartUploader uploader = new MultipartUploader(1048576)
      byte[] data = new byte[expectedSize]
      new Random().nextBytes(data)

    when:
      Blob blob = uploader.upload(storage, bucketName, 'vol-01/chap-01/control/test', new ByteArrayInputStream(data))

    then:
      blob.size == expectedSize
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
  }

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
  }
}
