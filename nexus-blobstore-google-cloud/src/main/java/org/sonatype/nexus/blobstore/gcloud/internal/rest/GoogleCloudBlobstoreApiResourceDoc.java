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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import static org.sonatype.nexus.rest.ApiDocConstants.API_BLOB_STORE;
import static org.sonatype.nexus.rest.ApiDocConstants.AUTHENTICATION_REQUIRED;
import static org.sonatype.nexus.rest.ApiDocConstants.INSUFFICIENT_PERMISSIONS;
import static org.sonatype.nexus.rest.ApiDocConstants.REPOSITORY_NOT_FOUND;

@Api(API_BLOB_STORE)
public interface GoogleCloudBlobstoreApiResourceDoc
{
  @ApiOperation("Get the configuration for a Google Cloud blob store")
  @ApiResponses(
      value = {
          @ApiResponse(code = 401, message = AUTHENTICATION_REQUIRED),
          @ApiResponse(code = 403, message = INSUFFICIENT_PERMISSIONS),
          @ApiResponse(code = 404, message = REPOSITORY_NOT_FOUND),
      }
  )
  GoogleCloudBlobstoreApiModel get(@ApiParam(value = "Name of the blob store") String blobStoreName);

  @ApiOperation("Create a Google Cloud blob store")
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Google Cloud blob store created"),
      @ApiResponse(code = 401, message = AUTHENTICATION_REQUIRED),
      @ApiResponse(code = 403, message = INSUFFICIENT_PERMISSIONS)
  })
  GoogleCloudBlobstoreApiModel create(GoogleCloudBlobstoreApiModel blobstoreApiModel) throws Exception;

  @ApiOperation("Update a Google Cloud blob store")
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Google Cloud blob store updated"),
      @ApiResponse(code = 401, message = AUTHENTICATION_REQUIRED),
      @ApiResponse(code = 403, message = INSUFFICIENT_PERMISSIONS),
      @ApiResponse(code = 404, message = REPOSITORY_NOT_FOUND)
  })
  GoogleCloudBlobstoreApiModel update(String blobStoreName, GoogleCloudBlobstoreApiModel blobstoreApiModel)
      throws Exception;
}
