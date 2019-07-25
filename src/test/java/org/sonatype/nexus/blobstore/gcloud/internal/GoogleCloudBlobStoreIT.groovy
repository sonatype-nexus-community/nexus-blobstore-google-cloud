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

import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.stream.StreamSupport

import org.sonatype.nexus.blobstore.BlobIdLocationResolver
import org.sonatype.nexus.blobstore.DefaultBlobIdLocationResolver
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
import org.sonatype.nexus.repository.storage.TempBlob
import org.sonatype.nexus.scheduling.PeriodicJobService
import org.sonatype.nexus.scheduling.PeriodicJobService.PeriodicJob

import com.codahale.metrics.MetricRegistry
import com.google.cloud.storage.Blob.BlobSourceOption
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Maps
import com.google.common.hash.Hashing
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static org.sonatype.nexus.blobstore.DirectPathLocationStrategy.DIRECT_PATH_PREFIX
import static org.sonatype.nexus.blobstore.gcloud.internal.AbstractGoogleClientFactory.KEEP_ALIVE_DURATION

class GoogleCloudBlobStoreIT
  extends Specification
{

  static final Logger log = LoggerFactory.getLogger(GoogleCloudBlobStoreIT.class)

  static final BlobStoreConfiguration config = new BlobStoreConfiguration()

  static final long quotaLimit = 512000L

  static String bucketName = "integration-test-${UUID.randomUUID().toString()}"

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

  MetricRegistry metricRegistry = Mock()

  GoogleCloudStorageFactory storageFactory = new GoogleCloudStorageFactory()

  static final BlobIdLocationResolver blobIdLocationResolver =  new DefaultBlobIdLocationResolver()

  GoogleCloudDatastoreFactory datastoreFactory = new GoogleCloudDatastoreFactory()

  BlobStoreQuotaService quotaService

  GoogleCloudBlobStore blobStore

  BlobStoreUsageChecker usageChecker = Mock()

  def setup() {
    quotaService = new BlobStoreQuotaServiceImpl([
        (SpaceUsedQuota.ID): new SpaceUsedQuota()
    ])

    config.name = 'GoogleCloudBlobStoreIT'
    config.attributes = [
        'google cloud storage'          : [
            bucket: bucketName,
            credential_file: this.getClass().getResource('/gce-credentials.json').getFile()
        ],
        (BlobStoreQuotaSupport.ROOT_KEY): [
            (BlobStoreQuotaSupport.TYPE_KEY): (SpaceUsedQuota.ID),
            (BlobStoreQuotaSupport.LIMIT_KEY): quotaLimit
        ]
    ]

    log.info("Integration test using bucket ${bucketName}")

    blobStore = new GoogleCloudBlobStore(storageFactory, blobIdLocationResolver, datastoreFactory,
        periodicJobService, new DryRunPrefix("TEST "), metricRegistry, quotaService, 60)
    blobStore.init(config)

    blobStore.start()

    usageChecker.test(_, _, _) >> true
  }

  def cleanup() {
    blobStore.stop()
    blobStore.remove()
  }

  def cleanupSpec() {
    Storage storage = new GoogleCloudStorageFactory().create(config)
    log.debug("Integration tests complete, deleting files from ${bucketName}...")
    // must delete all the files within the bucket before we can delete the bucket
    Iterator<com.google.cloud.storage.Blob> list = storage.list(bucketName,
        Storage.BlobListOption.prefix("")).iterateAll()
        .iterator()

    Iterable<com.google.cloud.storage.Blob> iterable = { _ -> list }
    StreamSupport.stream(iterable.spliterator(), true)
        .forEach({ b -> b.delete(BlobSourceOption.generationMatch()) })
    storage.delete(bucketName)
    log.info("bucket ${bucketName} deleted")
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
      Storage storage = storageFactory.create(config)
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

  def "undelete does nothing on non-existent blob"() {
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
      log.info("waiting for ${(KEEP_ALIVE_DURATION + 1000L) / 1000L} seconds any stale connections to close")
      sleep(KEEP_ALIVE_DURATION + 1000L)

      Blob blob2 = blobStore.create(new ByteArrayInputStream('hello'.getBytes()),
          [ (BlobStore.BLOB_NAME_HEADER): 'foo2',
            (BlobStore.CREATED_BY_HEADER): 'someuser' ] )
      assert blob2 != null
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
      TempBlob tempBlob = new TempBlob(blob, hashingStream.hashes(), true, blobStore)

      assert tempBlob != null
      assert tempBlob.blob.id.toString().startsWith('tmp$')
      // put the tempBlob into the final location
      Map<String, String> filtered = Maps.filterKeys(headers, { k -> !k.equals(BlobStore.TEMPORARY_BLOB_HEADER) })
      Blob result = blobStore.copy(tempBlob.blob.id, filtered)
      // close the tempBlob (results in deleteHard on the tempBlob)
      tempBlob.close()

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

  def createRegularFile(Storage storage, String path) {
    createRegularFile(storage, path, 'content')
  }

  def createRegularFile(Storage storage, String path, long size) {
    byte [] content = new byte[size]
    new Random().nextBytes(content)
    storage.create(BlobInfo.newBuilder(bucketName, path).build(), content)
  }
}
