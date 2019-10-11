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

import java.util.concurrent.TimeUnit
import java.util.stream.IntStream

import org.sonatype.nexus.blobstore.BlobIdLocationResolver
import org.sonatype.nexus.blobstore.DefaultBlobIdLocationResolver
import org.sonatype.nexus.blobstore.api.BlobId
import org.sonatype.nexus.blobstore.api.BlobStore
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration
import org.sonatype.nexus.scheduling.PeriodicJobService
import org.sonatype.nexus.scheduling.PeriodicJobService.PeriodicJob

import com.google.common.base.Stopwatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.util.concurrent.PollingConditions


class ShardedCounterMetricsStoreIT
  extends Specification
{

  static final Logger log = LoggerFactory.getLogger(ShardedCounterMetricsStoreIT.class)
  static final BlobStoreConfiguration config = new BlobStoreConfiguration()

  BlobIdLocationResolver blobIdLocationResolver =  new DefaultBlobIdLocationResolver()

  GoogleCloudDatastoreFactory datastoreFactory = new GoogleCloudDatastoreFactory()

  BlobStore blobStore = Mock()

  PeriodicJobService periodicJobService = Mock({
    schedule(_, _) >> new PeriodicJob() {
      @Override
      void cancel() {
      }
    }
  })

  ShardedCounterMetricsStore metricsStore

  def setupSpec() {
    config.name = "ShardedCounterMetricsStoreIT"
    config.attributes = [
        'google cloud storage': [
            credential_file: this.getClass().getResource('/gce-credentials.json').getFile()
        ]
    ]
  }
  def setup() {
    blobStore.getBlobStoreConfiguration() >> config
    metricsStore = new ShardedCounterMetricsStore(blobIdLocationResolver, datastoreFactory, periodicJobService)
    metricsStore.setBlobStore(blobStore)
    metricsStore.start()
  }

  def cleanup() {
    metricsStore.stop()
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

  def "eventually consistent getMetrics() results after concurrent adds/deletes"() {
    given:
      def number_of_records = 300
      def size_of_record = 1024L

      IntStream.range(0, number_of_records)
          .forEach({ i ->
            BlobId id = new BlobId(UUID.randomUUID().toString())
             if (i % 2 == 0) {
              metricsStore.recordAddition(id, size_of_record)
            } else {
              metricsStore.recordAddition(id, size_of_record)
              metricsStore.recordDeletion(id, size_of_record)
            }
          })
      metricsStore.flush()

    when:
      Stopwatch stopwatch = Stopwatch.createStarted()
      def metrics = metricsStore.metrics
      stopwatch.stop()
      log.info("stored {} records, getMetrics() call elapsed {}, result {} ", number_of_records, stopwatch, metrics)

    then:
      stopwatch.elapsed(TimeUnit.SECONDS) < 1
      def conditions = new PollingConditions(timeout: 5, initialDelay: 0, factor: 1)
        // datastore is eventually consistent
        // even though we have flushed, there are times that those writes are not immediately read-visible
      conditions.eventually {
        metrics.blobCount == number_of_records / 2
        metrics.totalSize == (number_of_records / 2) * size_of_record
        metrics = metricsStore.metrics
      }

  }
}
