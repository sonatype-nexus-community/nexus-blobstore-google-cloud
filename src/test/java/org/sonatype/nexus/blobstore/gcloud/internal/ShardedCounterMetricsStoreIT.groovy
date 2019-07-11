package org.sonatype.nexus.blobstore.gcloud.internal

import java.util.concurrent.TimeUnit
import java.util.stream.IntStream

import org.sonatype.nexus.blobstore.BlobIdLocationResolver
import org.sonatype.nexus.blobstore.DefaultBlobIdLocationResolver
import org.sonatype.nexus.blobstore.api.BlobId
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration
import org.sonatype.nexus.scheduling.PeriodicJobService
import org.sonatype.nexus.scheduling.PeriodicJobService.PeriodicJob

import com.google.common.base.Stopwatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification


class ShardedCounterMetricsStoreIT
  extends Specification
{

  static final Logger log = LoggerFactory.getLogger(ShardedCounterMetricsStoreIT.class)
  static final BlobStoreConfiguration config = new BlobStoreConfiguration()

  BlobIdLocationResolver blobIdLocationResolver =  new DefaultBlobIdLocationResolver()

  GoogleCloudDatastoreFactory datastoreFactory = new GoogleCloudDatastoreFactory()

  PeriodicJobService periodicJobService = Mock({
    schedule(_, _) >> new PeriodicJob() {
      @Override
      void cancel() {
      }
    }
  })

  ShardedCounterMetricsStore metricsStore

  def setupSpec() {
    config.name = 'ShardedCounterMetricsStoreIT'
    config.attributes = [
        'google cloud storage': [
            credential_file: this.getClass().getResource('/gce-credentials.json').getFile()
        ]
    ]
  }
  def setup() {
    metricsStore = new ShardedCounterMetricsStore(blobIdLocationResolver, datastoreFactory, periodicJobService)
    metricsStore.init(config)
  }

  def cleanup() {
    metricsStore.removeData()
  }

  def "getMetrics() shows empty when there are no entries stored"() {
    expect:
      def metrics = metricsStore.metrics
      metrics.blobCount == 0L
      metrics.totalSize == 0L
      metrics.unlimited
  }

  def "recordAddition is reflected accurately in getMetrics()"() {
    given:
      BlobId id = new BlobId(UUID.randomUUID().toString())
      metricsStore.recordAddition(id, 1024L)
      // forcing a flush guarantees we avoid racing the flush task
      metricsStore.flush()

    when:
      def metrics = metricsStore.metrics

    then:
      metrics.blobCount == 1L
      metrics.totalSize == 1024L
  }

  def "recordDeletion is reflected accurately in getMetrics()"() {
    given:
      BlobId id = new BlobId(UUID.randomUUID().toString())
      metricsStore.recordAddition(id, 1024L)
      metricsStore.recordDeletion(id, 1024L)
      // forcing a flush guarantees we avoid racing the flush task
      metricsStore.flush()

    when:
      def metrics = metricsStore.metrics

    then:
      metrics.blobCount == 0L
      metrics.totalSize == 0L
  }

  def "consistent getMetrics() results after concurrent adds/deletes"() {
    given:
      def number_of_records = 300
      def size_of_record = 1024L

      IntStream.range(0, number_of_records)
          .parallel()
          .forEach({ i ->
            BlobId id = new BlobId(UUID.randomUUID().toString())
             if (i % 2 == 0) {
              metricsStore.recordAddition(id, size_of_record)
            } else {
              metricsStore.recordAddition(id, size_of_record)
              metricsStore.recordDeletion(id, size_of_record)
            }
          })
      // with the preceding writes happening concurrently, there's a high likelihood we have deltas in the queue
      // forcing a flush here will act like a latch; block until the rate limiter lets us write to datastore again
      metricsStore.flush()

    when:
      Stopwatch stopwatch = Stopwatch.createStarted()
      def metrics = metricsStore.metrics
      stopwatch.stop()
      log.info("stored {} records, getMetrics() call elapsed {}", number_of_records, stopwatch)

    then:
      stopwatch.elapsed(TimeUnit.SECONDS) < 1
      metrics.blobCount == number_of_records / 2
      metrics.totalSize == (number_of_records / 2) * size_of_record
  }

}
