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
package org.sonatype.nexus.blobstore.gcloud.internal.rest;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.rest.BlobStoreApiSoftQuota;
import org.sonatype.nexus.common.collect.NestedAttributesMap;

import io.swagger.annotations.ApiModelProperty;

import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.BUCKET_KEY;
import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.CONFIG_KEY;
import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.LOCATION_KEY;
import static org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport.LIMIT_KEY;
import static org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport.ROOT_KEY;
import static org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport.TYPE_KEY;

/**
 * Transfer object model for REST API.
 */
public class GoogleCloudBlobstoreApiModel
{
  @NotBlank
  @ApiModelProperty("The name of the blob store")
  private String name;

  @NotBlank
  @ApiModelProperty("The name of the bucket in Google Cloud Storage")
  private String bucketName;

  @NotBlank
  @ApiModelProperty("The name of the region where the bucket is stored, e.g. us-central1")
  private String region;

  @ApiModelProperty("Settings to control the soft quota.")
  private BlobStoreApiSoftQuota softQuota;

  public GoogleCloudBlobstoreApiModel() {
  }

  GoogleCloudBlobstoreApiModel(BlobStoreConfiguration configuration) {
    this.name = configuration.getName();
    this.bucketName = configuration.attributes(CONFIG_KEY).get(BUCKET_KEY, String.class);
    this.region = configuration.attributes(CONFIG_KEY).get(LOCATION_KEY, String.class);

    NestedAttributesMap softQuotaAttributes = configuration.attributes(ROOT_KEY);
    if (softQuotaAttributes != null) {
      BlobStoreApiSoftQuota softQuota = new BlobStoreApiSoftQuota();
      softQuota.setLimit(configuration.attributes(ROOT_KEY).get(LIMIT_KEY, Long.class));
      softQuota.setType(configuration.attributes(ROOT_KEY).get(TYPE_KEY, String.class));
      this.softQuota = softQuota;
    }
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(final String bucketName) {
    this.bucketName = bucketName;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(final String region) {
    this.region = region;
  }

  public BlobStoreApiSoftQuota getSoftQuota() {
    return softQuota;
  }

  public void setSoftQuota(final BlobStoreApiSoftQuota softQuota) {
    this.softQuota = softQuota;
  }
}
