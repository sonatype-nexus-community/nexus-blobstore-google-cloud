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
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.AccumulatingBlobStoreMetrics;
import org.sonatype.nexus.blobstore.BlobStoreMetricsStoreSupport;
import org.sonatype.nexus.blobstore.PeriodicJobService;
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaService;
import org.sonatype.nexus.common.node.NodeAccess;

import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.common.collect.ImmutableMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Streams.stream;

@Named
public class GoogleCloudBlobStoreMetricsStore
    extends BlobStoreMetricsStoreSupport<GoogleCloudPropertiesFile>
{
  private Bucket bucket;

  @Inject
  public GoogleCloudBlobStoreMetricsStore(final PeriodicJobService jobService,
                                          final NodeAccess nodeAccess,
                                          final BlobStoreQuotaService quotaService,
                                          @Named("${nexus.blobstore.quota.warnIntervalSeconds:-60}")
                                          final int quotaCheckInterval)
  {
    super(nodeAccess, jobService, quotaService, quotaCheckInterval);
  }

  @Override
  protected void doStop() throws Exception {
    super.doStop();

    bucket = null;
  }

  @Override
  protected GoogleCloudPropertiesFile getProperties() {
    return new GoogleCloudPropertiesFile(bucket, nodeAccess.getId() + "-" + METRICS_FILENAME);
  }

  @Override
  protected AccumulatingBlobStoreMetrics getAccumulatingBlobStoreMetrics() {
    return new AccumulatingBlobStoreMetrics(0, 0, ImmutableMap.of("gcp", Long.MAX_VALUE), true);
  }

  public void setBucket(final Bucket bucket) {
    checkState(this.bucket == null, "Do not initialize twice");
    checkNotNull(bucket);
    this.bucket = bucket;
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

  protected Stream<GoogleCloudPropertiesFile> backingFiles() {
    if (bucket == null) {
      return Stream.empty();
    } else {
      return stream(bucket.list(BlobListOption.prefix(nodeAccess.getId())).iterateAll())
          .filter(b -> b.getName().endsWith(METRICS_FILENAME))
          .map(blob -> new GoogleCloudPropertiesFile(bucket, blob.getName()));
    }
  }
}
