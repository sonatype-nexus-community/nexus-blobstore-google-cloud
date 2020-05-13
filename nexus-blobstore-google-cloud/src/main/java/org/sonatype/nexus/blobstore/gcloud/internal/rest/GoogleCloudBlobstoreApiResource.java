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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore;
import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.rest.Resource;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.BUCKET_KEY;
import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.CONFIG_KEY;
import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.LOCATION_KEY;
import static org.sonatype.nexus.blobstore.gcloud.internal.rest.GoogleCloudBlobstoreApiResource.RESOURCE_URI;
import static org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport.LIMIT_KEY;
import static org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport.ROOT_KEY;
import static org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport.TYPE_KEY;
import static org.sonatype.nexus.rest.APIConstants.BETA_API_PREFIX;

/**
 * REST API for managing Google Cloud blob stores.
 */
@Named
@Singleton
@Path(RESOURCE_URI)
@Produces(APPLICATION_JSON)
@Consumes(APPLICATION_JSON)
public class GoogleCloudBlobstoreApiResource
    extends ComponentSupport
    implements Resource, GoogleCloudBlobstoreApiResourceDoc
{
  static final String RESOURCE_URI = BETA_API_PREFIX + "/blobstores/google";

  static final int ONE_MILLION = 1_000_000;

  private final BlobStoreManager blobStoreManager;

  @Inject
  public GoogleCloudBlobstoreApiResource(BlobStoreManager blobStoreManager) {
    this.blobStoreManager = blobStoreManager;
  }

  @GET
  @RequiresAuthentication
  @Path("/{name}")
  @RequiresPermissions("nexus:blobstores:read")
  @Override
  public GoogleCloudBlobstoreApiModel get(@PathParam("name") final String name) {
    BlobStore blobStore = blobStoreManager.get(name);
    log.error("{}", blobStore);
    if (blobStore == null) {
      return null;
    }
    BlobStoreConfiguration config = confirmType(blobStore.getBlobStoreConfiguration());
    return new GoogleCloudBlobstoreApiModel(config);
  }

  @POST
  @RequiresAuthentication
  @RequiresPermissions("nexus:blobstores:create")
  @Override
  public GoogleCloudBlobstoreApiModel create(@Valid final GoogleCloudBlobstoreApiModel model)
      throws Exception
  {
    if (blobStoreManager.get(model.getName()) != null) {
      throw new IllegalArgumentException("A blob store with that name already exists");
    }
    BlobStoreConfiguration config = blobStoreManager.newConfiguration();
    config.setType(GoogleCloudBlobStore.TYPE);
    merge(config, model);
    BlobStore blobStore = blobStoreManager.create(config);
    return new GoogleCloudBlobstoreApiModel(blobStore.getBlobStoreConfiguration());
  }

  @PUT
  @RequiresAuthentication
  @Path("/{name}")
  @RequiresPermissions("nexus:blobstores:update")
  @Override
  public GoogleCloudBlobstoreApiModel update(@PathParam("name") final String name,
                                             @Valid final GoogleCloudBlobstoreApiModel model)
      throws Exception
  {
    BlobStore existing = blobStoreManager.get(name);
    if (existing == null) {
      return null;
    }
    BlobStoreConfiguration config = confirmType(existing.getBlobStoreConfiguration());
    merge(config, model);

    BlobStore blobStore = blobStoreManager.update(config);
    return new GoogleCloudBlobstoreApiModel(blobStore.getBlobStoreConfiguration());
  }

  /**
   * @param config to check
   * @return the configuration if it is of {@link GoogleCloudBlobStore#TYPE}
   * @throws IllegalArgumentException if it is any other type
   */
  BlobStoreConfiguration confirmType(BlobStoreConfiguration config) {
    if (!GoogleCloudBlobStore.TYPE.equals(config.getType())) {
      throw new IllegalArgumentException("Use this API only for blob stores with type " + GoogleCloudBlobStore.TYPE);
    }
    return config;
  }

  /**
   * @param config the configuration to be updated
   * @param blobstoreApiModel the source of new values
   */
  void merge(BlobStoreConfiguration config, GoogleCloudBlobstoreApiModel blobstoreApiModel) {
    config.setName(blobstoreApiModel.getName());
    NestedAttributesMap bucket = config.attributes(CONFIG_KEY);
    bucket.set(BUCKET_KEY, blobstoreApiModel.getBucketName());
    bucket.set(LOCATION_KEY, blobstoreApiModel.getRegion());

    if (blobstoreApiModel.getSoftQuota() != null ) {
      NestedAttributesMap softQuota = config.attributes(ROOT_KEY);
      softQuota.set(TYPE_KEY, checkNotNull(blobstoreApiModel.getSoftQuota().getType()));
      final Long softQuotaLimit = checkNotNull(blobstoreApiModel.getSoftQuota().getLimit());
      softQuota.set(LIMIT_KEY, softQuotaLimit * ONE_MILLION);
    }
  }
}
