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

import spock.lang.Ignore

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.stream.StreamSupport

import org.sonatype.nexus.blobstore.BlobIdLocationResolver
import org.sonatype.nexus.blobstore.DefaultBlobIdLocationResolver
import org.sonatype.nexus.blobstore.MockBlobStoreConfiguration
import org.sonatype.nexus.blobstore.api.Blob
import org.sonatype.nexus.blobstore.api.BlobAttributes
import org.sonatype.nexus.blobstore.api.BlobId
import org.sonatype.nexus.blobstore.api.BlobStore
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration
import org.sonatype.nexus.blobstore.api.BlobStoreUsageChecker
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaService
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport
import org.sonatype.nexus.blobstore.quota.internal.BlobStoreQuotaServiceImpl
import org.sonatype.nexus.blobstore.quota.internal.SpaceUsedQuota
import org.sonatype.nexus.common.hash.HashAlgorithm
import org.sonatype.nexus.common.hash.MultiHashingInputStream
import org.sonatype.nexus.common.log.DryRunPrefix
import org.sonatype.nexus.repository.view.payloads.TempBlob
import org.sonatype.nexus.scheduling.PeriodicJobService
import org.sonatype.nexus.scheduling.PeriodicJobService.PeriodicJob

import com.codahale.metrics.MetricRegistry
import com.google.cloud.storage.Blob.BlobSourceOption
import com.google.cloud.storage.Storage
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import com.google.common.hash.Hashing
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static org.sonatype.nexus.blobstore.DirectPathLocationStrategy.DIRECT_PATH_PREFIX
import static org.sonatype.nexus.blobstore.gcloud.internal.AbstractGoogleClientFactory.KEEP_ALIVE_DURATION

class GoogleCloudBlobStoreIT
  extends Specification
{

  static final Logger log = LoggerFactory.getLogger(GoogleCloudBlobStoreIT.class)

  static BlobStoreConfiguration config = new MockBlobStoreConfiguration()

  static final long quotaLimit = 512000L

  static final String uid = UUID.randomUUID().toString().substring(0,4)
  static String bucketName = "integration-test-${uid}"

  def hashAlgorithms = ImmutableList.of(
      new HashAlgorithm("sha1", Hashing.sha1()),
      new HashAlgorithm("md5", Hashing.md5()))

  PeriodicJobService periodicJobService = Mock({
    schedule(_, _) >> new PeriodicJob() {
      @Override
      void cancel() {
      }
    }
  })

  MetricRegistry metricRegistry = new MetricRegistry()

  GoogleCloudStorageFactory storageFactory = new GoogleCloudStorageFactory()

  static final BlobIdLocationResolver blobIdLocationResolver =  new DefaultBlobIdLocationResolver()

  GoogleCloudDatastoreFactory datastoreFactory = new GoogleCloudDatastoreFactory()

  BlobStoreQuotaService quotaService

  GoogleCloudBlobStore blobStore

  BlobStoreUsageChecker usageChecker = Mock()

  // chunkSize = 2 MB
  // the net effect of this is all tests except for "create large file" will be single thread
  // create large file will end up using max chunks
  MultipartUploader uploader = new MultipartUploader(metricRegistry, 2097152)

  def setup() {

    quotaService = new BlobStoreQuotaServiceImpl([
        (SpaceUsedQuota.ID): new SpaceUsedQuota()
    ])

    config = makeConfig("GoogleCloudBlobStoreIT-${uid}", bucketName)

    log.info("Integration test using bucket ${bucketName}")

    blobStore = new GoogleCloudBlobStore(storageFactory, blobIdLocationResolver, periodicJobService,
        datastoreFactory, new DryRunPrefix("TEST "), uploader, metricRegistry, quotaService, 60)
    blobStore.init(config)
    blobStore.start()

    usageChecker.test(_, _, _) >> true
  }

  def cleanup() {
    blobStore.stop()
    blobStore.remove()
  }

  def cleanupSpec() {
    cleanupBucket(config, bucketName)
  }

  def "isWritable true for buckets created by the Integration Test"() {
    expect:
      blobStore.isWritable()
  }

  def "isGroupable is true"() {
    expect:
      blobStore.isGroupable()
  }

  def "getDirectPathBlobIdStream returns empty stream for missing prefix"() {
    given:

    when:
      Stream<?> s = blobStore.getDirectPathBlobIdStream('notpresent')

    then:
      !s.iterator().hasNext()
  }

  def "getDirectPathBlobIdStream returns expected content"() {
    given:
      // mimic some RHC content, which is stored as directpath blobs
      blobStore.create(new ByteArrayInputStream('some text content'.getBytes()),
          [ (BlobStore.BLOB_NAME_HEADER): 'health-check/repo1/report.properties',
            (BlobStore.CREATED_BY_HEADER): 'someuser',
            (BlobStore.DIRECT_PATH_BLOB_HEADER): 'true'] )
       blobStore.create(new ByteArrayInputStream('some css content'.getBytes()),
          [ (BlobStore.BLOB_NAME_HEADER): 'health-check/repo1/details/bootstrap.min.css',
            (BlobStore.CREATED_BY_HEADER): 'someuser',
            (BlobStore.DIRECT_PATH_BLOB_HEADER): 'true'] )

    when:
     Stream<BlobId> stream = blobStore.getDirectPathBlobIdStream('health-check/repo1')

    then:
      List<BlobId> results = stream.collect(Collectors.toList())
      results.size() == 2
      results.contains(new BlobId("${DIRECT_PATH_PREFIX}health-check/repo1/report.properties"))
      results.contains(new BlobId("${DIRECT_PATH_PREFIX}health-check/repo1/details/bootstrap.min.css"))
  }

  def "undelete successfully makes blob accessible"() {
    given:
      Blob blob = blobStore.create(new ByteArrayInputStream('hello'.getBytes()),
          [ (BlobStore.BLOB_NAME_HEADER): 'foo',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null
      assert blobStore.getBlobAttributes(blob.id) != null
      assert blobStore.delete(blob.id, 'testing')
      BlobAttributes deletedAttributes = blobStore.getBlobAttributes(blob.id)
      assert deletedAttributes.deleted
      assert deletedAttributes.deletedReason == 'testing'

    when:
      !blobStore.undelete(usageChecker, blob.id, deletedAttributes, false)

    then:
      Blob after = blobStore.get(blob.id)
      after != null
      BlobAttributes attributesAfter = blobStore.getBlobAttributes(blob.id)
      !attributesAfter.deleted
  }

  def "undelete does nothing when dry run is true"() {
    given:
      Blob blob = blobStore.create(new ByteArrayInputStream('hello'.getBytes()),
          [ (BlobStore.BLOB_NAME_HEADER): 'foo',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null
      BlobAttributes attributes = blobStore.getBlobAttributes(blob.id)
      assert attributes != null
      assert blobStore.delete(blob.id, 'testing')
      BlobAttributes deletedAttributes = blobStore.getBlobAttributes(blob.id)
      assert deletedAttributes.deleted

    when:
      blobStore.undelete(usageChecker, blob.id, attributes, true)

    then:
      Blob after = blobStore.get(blob.id)
      after == null
      BlobAttributes attributesAfter = blobStore.getBlobAttributes(blob.id)
      attributesAfter.deleted
  }

  def "undelete does nothing on nonexistent blob"() {
    expect:
      BlobAttributes attributes = Mock()
      !blobStore.undelete(usageChecker, new BlobId("nonexistent"), attributes, false)
  }

  def "create after keep-alive window closes is still successful"() {
    expect:
      Blob blob = blobStore.create(new ByteArrayInputStream('hello'.getBytes()),
          [ (BlobStore.BLOB_NAME_HEADER): 'foo1',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null
      // sit for at least the time on our keep alives, so that any held connections close
      log.info("waiting for ${(KEEP_ALIVE_DURATION + 1000L) / 1000L} seconds so any stale connections close")
      sleep(KEEP_ALIVE_DURATION + 1000L)

      Blob blob2 = blobStore.create(new ByteArrayInputStream('hello'.getBytes()),
          [ (BlobStore.BLOB_NAME_HEADER): 'foo2',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob2 != null
  }

  def "copy matches expectations" () {
    given:
      def expectedSize = 2048
      byte[] data = new byte[expectedSize]
      new Random().nextBytes(data)
      Blob blob = blobStore.create(new ByteArrayInputStream(data),
          [ (BlobStore.BLOB_NAME_HEADER): 'foo1',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null

      def headers = [ (BlobStore.BLOB_NAME_HEADER): 'foo2',
                      (BlobStore.CREATED_BY_HEADER): 'someuser' ]
    when:
      def moved = blobStore.copy(blob.id, headers)

    then:
      // existing still present
      Blob existing = blobStore.get(blob.id)
      assert existing != null
      Blob after = blobStore.get(moved.id)
      assert after != null
      assert after.id != existing.id
      assert after.getInputStream().bytes == existing.getInputStream().bytes
  }

  /**
   * This test is disabled by default as it can be time consuming.
   *
   * To enable it, perform the following:
   *
   * <ol>
   *   <li>Open a terminal and navigate to src/test/resources within this project.</li>
   *   <li>Execute the following to create a large file: `dd if=/dev/urandom of=large_file bs=1m count=500`</li>
   * </ol>
   *
   * On my workstation a few hundred miles from the GCP region with a typical consumer grade ISP (limited to 6-7 Mbps
   * upload), this test completes successfully in around 10 minutes (with the 500 MB file generated by the dd command).
   */
  @IgnoreIf({ getClass().getResource('/large_file') == null })
  def "create large file" () {
    given:
      def url = getClass().getResource('/large_file')

    when:
      Blob blob = blobStore.create(url.newInputStream(),
          [ (BlobStore.BLOB_NAME_HEADER): 'testing-largefile',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )

    then:
      def blobId = blob.id
      assert blobId != null
      blobStore.get(blobId) != null

    cleanup:
      blobStore.deleteHard(blobId)
  }

  def "deleteHard matches expectations" () {
    given:
      def expectedSize = 2048
      byte[] data = new byte[expectedSize]
      new Random().nextBytes(data)
      Blob blob = blobStore.create(new ByteArrayInputStream(data),
          [ (BlobStore.BLOB_NAME_HEADER): 'testing-deleteHard',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null

    when:
      def deleted = blobStore.deleteHard(blob.id)

    then:
      deleted
      blobStore.get(blob.id) == null
      blobStore.getBlobAttributes(blob.id) == null
  }

  def "setBlobAttributes matches expectations" () {
    given:
      Blob blob = blobStore.create(new ByteArrayInputStream('hello'.bytes),
          [ (BlobStore.BLOB_NAME_HEADER): 'foo1',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null

      def attributes = blobStore.getBlobAttributes(blob.id)
      attributes.headers.put('somekey', 'somevalue')

    when:
      blobStore.setBlobAttributes(blob.id, attributes)

    then:
      def updated = blobStore.getBlobAttributes(blob.id)
      updated.headers.get(BlobStore.BLOB_NAME_HEADER) == 'foo1'
      updated.headers.get(BlobStore.CREATED_BY_HEADER) == 'someuser'
      updated.headers.get('somekey') == 'somevalue'

  }

  def "mimic storage facet write-temp-and-move"() {
    given:
      def expectedSize = 2048
      byte[] data = new byte[expectedSize]
      new Random().nextBytes(data)
      def headers = ImmutableMap.of(
          BlobStore.BLOB_NAME_HEADER, "temp",
          BlobStore.CREATED_BY_HEADER, "system",
          BlobStore.CREATED_BY_IP_HEADER, "system",
          BlobStore.TEMPORARY_BLOB_HEADER, "")

    expect:
      // write tempBlob
      MultiHashingInputStream hashingStream = new MultiHashingInputStream(hashAlgorithms,
          new ByteArrayInputStream(data))
      Blob blob = blobStore.create(hashingStream, headers)
      blobStore.flushMetricsStore()
      blobStore.getMetrics().blobCount == 1L
      blobStore.getMetrics().totalSize == 2048

      TempBlob tempBlob = new TempBlob(blob, hashingStream.hashes(), true, blobStore)

      assert tempBlob != null
      assert tempBlob.blob.id.toString().startsWith('tmp$')
      // put the tempBlob into the final location
      Map<String, String> filtered = Maps.filterKeys(headers, { k -> !k.equals(BlobStore.TEMPORARY_BLOB_HEADER) })
      Blob result = blobStore.copy(tempBlob.blob.id, filtered)
      blobStore.flushMetricsStore()
      blobStore.getMetrics().blobCount == 2L
      blobStore.getMetrics().totalSize == 4096
      // close the tempBlob (results in deleteHard on the tempBlob)
      tempBlob.close()

      blobStore.flushMetricsStore()
      blobStore.getMetrics().blobCount == 1L
      blobStore.getMetrics().totalSize == 2048
      Blob retrieve = blobStore.get(result.getId())
      assert retrieve != null
  }

  def "soft delete results in BlobId being tracked correctly in DeletedBlobIndex"() {
    given: 'we have stored a blob'
      Blob blob = blobStore.create(new ByteArrayInputStream('hello'.getBytes()),
          [ (BlobStore.BLOB_NAME_HEADER): 'foo1',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null

    when: 'we soft delete it'
      blobStore.delete(blob.id, "integration test")

    then: 'the blobId is present in the DeletedBlobIndex'
      blobStore.getDeletedBlobIndex().getContents().anyMatch(Predicate.isEqual(blob.id))

    cleanup:
      blobStore.deleteHard(blob.id)
  }

  @Ignore
  def "soft delete experiment with compact"() {
    given: 'we have stored and soft deleted 101 blobs'
      int toCreate = 101
      List<BlobId> created = new ArrayList<>()
      for(int i = 0; i < toCreate; i++) {
        Blob blob = blobStore.create(new ByteArrayInputStream('hello'.getBytes()),
                [ (BlobStore.BLOB_NAME_HEADER): "foo${i}".toString(),
                  (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
        assert blob != null
        created.add(blob.id)
      }
      // confirm metrics are written out
      blobStore.flushMetricsStore()
      // confirm we show expected blob count and total size
      assert blobStore.getMetrics().getBlobCount() == toCreate
      assert blobStore.getMetrics().getTotalSize() == toCreate * 5

      // now soft-delete all of the blobs
      created.each {blobStore.delete(it, "integration test") }

    when: 'we run compaction'
      blobStore.compact()
      blobStore.flushMetricsStore()

    then: 'the DeletedBlobIndex is empty'
      0L == blobStore.getDeletedBlobIndex().getContents().count()
      0L == blobStore.getMetrics().getBlobCount()
      0L == blobStore.getMetrics().getTotalSize()

    cleanup:
      created.each {blobStore.deleteHard(it) }
  }

  def "compaction after soft delete results in empty DeletedBlobIndex"() {
    given: 'we have stored a blob, and we soft deleted it'
      Blob blob = blobStore.create(new ByteArrayInputStream('hello'.getBytes()),
          [ (BlobStore.BLOB_NAME_HEADER): 'foo1',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null
      blobStore.delete(blob.id, "integration test")
      assert blobStore.getDeletedBlobIndex().getContents().iterator().hasNext()

    when: 'we run compaction'
      blobStore.compact(null)

    then: 'the blobId is no longer present in the DeletedBlobIndex'
      blobStore.getDeletedBlobIndex().getContents().count() == 0L
  }

  def "quota violation properly reported when exceeded"() {
    given:
      def expectedSize = quotaLimit / 10
      for (int i = 0; i < 9; i++) {
        byte[] data = new byte[expectedSize]
        new Random().nextBytes(data)
        Blob blob = blobStore.create(new ByteArrayInputStream(data),
            [ (BlobStore.BLOB_NAME_HEADER): "foo${i}".toString(),
              (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
        assert blob != null
      }

      // force a metrics store flush to guarantee we don't have any pending deltas to store
      blobStore.flushMetricsStore()

      def quotaResult = quotaService.checkQuota(blobStore)
      assert !quotaResult.violation

      // 1 byte over should trigger quota violation
      byte[] data = new byte[expectedSize + 1]
      new Random().nextBytes(data)
      Blob blob = blobStore.create(new ByteArrayInputStream(data),
          [ (BlobStore.BLOB_NAME_HEADER): 'overquota',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null
      blobStore.flushMetricsStore()

    when:
      quotaResult = quotaService.checkQuota(blobStore)

    then:
      def conditions = new PollingConditions(timeout: 5, initialDelay: 0, factor: 1)
      // datastore is eventually consistent
      // even though we have flushed, there are times that those writes are not immediately read-visible
      conditions.eventually {
        quotaResult.violation
        quotaResult = quotaService.checkQuota(blobStore)
      }
  }

  def "metadata is multi-tenant"() {
    given:
      // we already have one blobstore
      // make a second
      def bucket2 = "multi-tenancy-test-${uid}"
      def config2 = makeConfig("multi-tenant-test-${uid + 'b'}", bucket2)
      def blobStore2 = new GoogleCloudBlobStore(storageFactory, blobIdLocationResolver, periodicJobService,
          datastoreFactory, new DryRunPrefix("TEST "), uploader, metricRegistry, quotaService, 60)
      blobStore2.init(config2)
      blobStore2.start()

      // write one file to blobstore1
      byte[] data = new byte[256]
      new Random().nextBytes(data)
      blobStore.create(new ByteArrayInputStream(data),
          [ (BlobStore.BLOB_NAME_HEADER): 'fileinblobstore1',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      // write a different size file to blobstore2
      byte[] data2 = new byte[312]
      new Random().nextBytes(data2)
      blobStore2.create(new ByteArrayInputStream(data2),
          [ (BlobStore.BLOB_NAME_HEADER): 'fileinblobstore2',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      // write a second file to blobstore2
      byte[] data3 = new byte[112]
      new Random().nextBytes(data2)
      Blob blob3 = blobStore2.create(new ByteArrayInputStream(data3),
          [ (BlobStore.BLOB_NAME_HEADER): 'file2inblobstore2',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      // soft delete the second file
      blobStore2.delete(blob3.id, 'testing')

      blobStore.flushMetricsStore()
      blobStore2.flushMetricsStore()

    when:
      // get metrics data for each
      def metrics1 = blobStore.metrics
      def metrics2 = blobStore2.metrics

    then:
      // assert deleted blob index is separate
      blobStore.getDeletedBlobIndex().contents.count() == 0L
      blobStore2.getDeletedBlobIndex().contents.count() == 1L
      // assert metrics data separate
      def conditions = new PollingConditions(timeout: 5, initialDelay: 0, factor: 1)
      conditions.eventually {
        metrics1.totalSize == 256L
        metrics2.totalSize == 312L
        metrics1 = blobStore.metrics
        metrics2 = blobStore2.metrics
      }

    cleanup:
      blobStore2.stop()
      blobStore2.remove()
      cleanupBucket(config2, bucket2)
  }

  def "getRawObjectAccess is unsupported"() {
    when:
      blobStore.rawObjectAccess.getRawObject()
    then:
      thrown UnsupportedOperationException
  }

  def "getBlobIdUpdatedSinceStream matches expectations"() {
    given:
      OffsetDateTime prior = Instant.now().atOffset(ZoneOffset.UTC)
      Blob blob = blobStore.create(new ByteArrayInputStream('hello'.getBytes()),
            [ (BlobStore.BLOB_NAME_HEADER): 'foo',
              (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob != null
      BlobAttributes attributes = blobStore.getBlobAttributes(blob.id)
      assert attributes != null

      OffsetDateTime after = Instant.now().atOffset(ZoneOffset.UTC);

    when:
      Stream<BlobId> updatedPrior = blobStore.getBlobIdUpdatedSinceStream(prior)
      Stream<BlobId> updatedAfter = blobStore.getBlobIdUpdatedSinceStream(after)

    then:
      updatedPrior.anyMatch({ id -> blob.id == id} )
      !updatedAfter.anyMatch({ id -> blob.id == id} )

    cleanup:
      blobStore.deleteHard(blob.id)
  }

  def makeConfig(String name, String bucket) {
    config.name = name
    config.attributes = [
        'google cloud storage': [
            bucket: bucket,
            location: 'us-central1',
            credential_file: this.getClass().getResource('/gce-credentials.json').getFile()
        ],
        (BlobStoreQuotaSupport.ROOT_KEY): [
            (BlobStoreQuotaSupport.TYPE_KEY): (SpaceUsedQuota.ID),
            (BlobStoreQuotaSupport.LIMIT_KEY): quotaLimit
        ]
    ]
    return config
  }

  static def cleanupBucket(def config, def bucket) {
    Storage storage = new GoogleCloudStorageFactory().create(config)
    log.debug("Deleting files from ${bucket}...")
    // must delete all the files within the bucket before we can delete the bucket
    Iterator<com.google.cloud.storage.Blob> list = storage.list(bucket,
        Storage.BlobListOption.prefix("")).iterateAll()
        .iterator()

    Iterable<com.google.cloud.storage.Blob> iterable = { _ -> list }
    StreamSupport.stream(iterable.spliterator(), true)
        .forEach({ b -> b.delete(BlobSourceOption.generationMatch()) })
    storage.delete(bucket)
    log.info("bucket ${bucket} deleted")
  }
}
