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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.AccumulatingBlobStoreMetrics;
import org.sonatype.nexus.blobstore.PeriodicJobService;
import org.sonatype.nexus.blobstore.PeriodicJobService.PeriodicJob;
import org.sonatype.nexus.blobstore.api.BlobStoreMetrics;
import org.sonatype.nexus.common.node.NodeAccess;
import org.sonatype.nexus.common.stateguard.Guarded;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Long.parseLong;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.STARTED;

@Named
public class GoogleCloudBlobStoreMetricsStore
    extends StateGuardLifecycleSupport
{
  private static final String METRICS_SUFFIX = "-metrics";

  private static final String METRICS_EXTENSION = ".properties";

  private static final String TOTAL_SIZE_PROP_NAME = "totalSize";

  private static final String BLOB_COUNT_PROP_NAME = "blobCount";

  private static final int METRICS_FLUSH_PERIOD_SECONDS = 2;

  private final PeriodicJobService jobService;

  private AtomicLong blobCount;

  private final NodeAccess nodeAccess;

  private AtomicLong totalSize;

  private AtomicBoolean dirty;

  private PeriodicJob metricsWritingJob;

  private GoogleCloudPropertiesFile propertiesFile;

  private Bucket bucket;

  @Inject
  public GoogleCloudBlobStoreMetricsStore(final PeriodicJobService jobService, final NodeAccess nodeAccess) {
    this.jobService = checkNotNull(jobService);
    this.nodeAccess = checkNotNull(nodeAccess);
  }

  @Override
  protected void doStart() throws Exception {
    blobCount = new AtomicLong();
    totalSize = new AtomicLong();
    dirty = new AtomicBoolean();

    propertiesFile = new GoogleCloudPropertiesFile(bucket, nodeAccess.getId() + METRICS_SUFFIX + METRICS_EXTENSION);
    if (propertiesFile.exists()) {
      log.info("Loading blob store metrics file {}", propertiesFile);
      propertiesFile.load();
      readProperties();
    }
    else {
      log.info("Blob store metrics file {} not found - initializing at zero.", propertiesFile);
      updateProperties();
      propertiesFile.store();
    }

    jobService.startUsing();
    metricsWritingJob = jobService.schedule(() -> {
      try {
        if (dirty.compareAndSet(true, false)) {
          updateProperties();
          log.trace("Writing blob store metrics to {}", propertiesFile);
          propertiesFile.store();
        }
      }
      catch (Exception e) {
        // Don't propagate, as this stops subsequent executions
        log.error("Cannot write blob store metrics", e);
      }
    }, METRICS_FLUSH_PERIOD_SECONDS);
  }

  @Override
  protected void doStop() throws Exception {
    metricsWritingJob.cancel();
    metricsWritingJob = null;
    jobService.stopUsing();

    blobCount = null;
    totalSize = null;
    dirty = null;

    propertiesFile = null;
  }

  public void setBucket(final Bucket bucket) {
    checkState(this.bucket == null, "Do not initialize twice");
    checkNotNull(bucket);
    this.bucket = bucket;
  }

  @Guarded(by = STARTED)
  public BlobStoreMetrics getMetrics() {
    Stream<GoogleCloudPropertiesFile> blobStoreMetricsFiles = backingFiles();
    return getCombinedMetrics(blobStoreMetricsFiles);
  }

  private BlobStoreMetrics getCombinedMetrics(final Stream<GoogleCloudPropertiesFile> blobStoreMetricsFiles) {
    AccumulatingBlobStoreMetrics blobStoreMetrics = new AccumulatingBlobStoreMetrics(0, 0,
        ImmutableMap.of("gcp", Long.MAX_VALUE), true);

    blobStoreMetricsFiles.forEach(metricsFile -> {
      try {
        metricsFile.load();
        blobStoreMetrics.addBlobCount(parseLong(metricsFile.getProperty(BLOB_COUNT_PROP_NAME, "0")));
        blobStoreMetrics.addTotalSize(parseLong(metricsFile.getProperty(TOTAL_SIZE_PROP_NAME, "0")));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    return blobStoreMetrics;
  }

  @Guarded(by = STARTED)
  public void recordAddition(final long size) {
    blobCount.incrementAndGet();
    totalSize.addAndGet(size);
    dirty.set(true);
  }

  @Guarded(by = STARTED)
  public void recordDeletion(final long size) {
    blobCount.decrementAndGet();
    totalSize.addAndGet(-size);
    dirty.set(true);
  }

  public void remove() {
    backingFiles().forEach(metricsFile -> {
      try {
        metricsFile.remove();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  private Stream<GoogleCloudPropertiesFile> backingFiles() {
    if (bucket == null) {
      return Stream.empty();
    } else {
      return Streams.stream(bucket.list(BlobListOption.prefix(nodeAccess.getId())).iterateAll())
          .filter(b -> b.getName().endsWith(METRICS_EXTENSION))
          .map(blob -> new GoogleCloudPropertiesFile(bucket, blob.getName()));
    }
  }

  private void updateProperties() {
    propertiesFile.setProperty(TOTAL_SIZE_PROP_NAME, totalSize.toString());
    propertiesFile.setProperty(BLOB_COUNT_PROP_NAME, blobCount.toString());
  }

  private void readProperties() {
    String size = propertiesFile.getProperty(TOTAL_SIZE_PROP_NAME);
    if (size != null) {
      totalSize.set(parseLong(size));
    }

    String count = propertiesFile.getProperty(BLOB_COUNT_PROP_NAME);
    if (count != null) {
      blobCount.set(parseLong(count));
    }
  }
}
