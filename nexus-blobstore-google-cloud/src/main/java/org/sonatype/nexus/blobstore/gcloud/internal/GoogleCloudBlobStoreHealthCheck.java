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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;

import com.codahale.metrics.health.HealthCheck;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

@Named("Google Cloud Blob Stores")
public class GoogleCloudBlobStoreHealthCheck
    extends HealthCheck
{

  private final Provider<BlobStoreManager> blobStoreManagerProvider;

  @Inject
  public GoogleCloudBlobStoreHealthCheck(final Provider<BlobStoreManager> blobStoreManagerProvider)
  {
    this.blobStoreManagerProvider = checkNotNull(blobStoreManagerProvider);
  }

  @Override
  protected Result check() {
    Iterable<BlobStore> blobstoreItr = blobStoreManagerProvider.get().browse();
    Map<String, Long> googleBlobstores = StreamSupport.stream(blobstoreItr.spliterator(), false)
        .filter(b -> b instanceof GoogleCloudBlobStore)
        .collect(
            Collectors.toMap(
                blobStore -> blobStore.getBlobStoreConfiguration().getName(),
                blobStore -> ((GoogleCloudBlobStore) blobStore).getSoftDeletedBlobCount())
        );

    List<String> violations = new ArrayList<>();
    for(Entry<String, Long> entry: googleBlobstores.entrySet()) {
      if (entry.getValue() >= DeletedBlobIndex.WARN_LIMIT) {
        violations.add(format("%s has %s soft-deleted blobs awaiting compaction", entry.getKey(), entry.getValue()));
      }
    }

    if (violations.isEmpty()) {
      return Result.healthy(format("%s Google Cloud Blob Store(s) are nominal", googleBlobstores.keySet().size()));
    } else {
      return Result.unhealthy(
          format("The following Google Cloud Blob Stores would benefit from a Compact Task:<br>%s",
              String.join("<br>", violations)));
    }
  }
}
