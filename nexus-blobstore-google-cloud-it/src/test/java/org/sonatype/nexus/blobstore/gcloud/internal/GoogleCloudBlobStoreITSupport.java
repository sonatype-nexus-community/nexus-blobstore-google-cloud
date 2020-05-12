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

import java.io.File;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.gcloud.internal.fixtures.RepositoryRuleGoogleCloud;
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport;
import org.sonatype.nexus.blobstore.quota.internal.SpaceUsedQuota;
import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.testsuite.testsupport.RepositoryITSupport;

import org.junit.Rule;

public class GoogleCloudBlobStoreITSupport
    extends RepositoryITSupport
{

  @Inject
  protected BlobStoreManager blobStoreManager;

  @Rule
  public RepositoryRuleGoogleCloud nxrm = new RepositoryRuleGoogleCloud(() -> repositoryManager);

  @Override
  protected RepositoryRuleGoogleCloud createRepositoryRule() {
    return new RepositoryRuleGoogleCloud(() -> repositoryManager);
  }

  public GoogleCloudBlobStoreITSupport() {
  }

  public BlobStoreConfiguration newConfiguration(final String name, final String bucketName,
                                                 @Nullable final File credentialFile) {
    BlobStoreConfiguration configuration = blobStoreManager.newConfiguration();
    configuration.setName(name);
    configuration.setType("Google Cloud Storage");
    NestedAttributesMap configMap = configuration.attributes("google cloud storage");
    configMap.set("bucket", bucketName);
    configMap.set("location", "us-central1");
    if (credentialFile != null) {
      configMap.set("credential_file", credentialFile.getAbsolutePath());
    }
    NestedAttributesMap quotaMap = configuration.attributes(BlobStoreQuotaSupport.ROOT_KEY);
    quotaMap.set(BlobStoreQuotaSupport.TYPE_KEY, SpaceUsedQuota.ID);
    quotaMap.set(BlobStoreQuotaSupport.LIMIT_KEY, 512000L);

    return configuration;
  }
}
