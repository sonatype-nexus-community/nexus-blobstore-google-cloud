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

import java.io.FileInputStream;

import javax.inject.Named;

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import org.apache.shiro.util.StringUtils;

import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.CONFIG_KEY;
import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.CREDENTIAL_FILE_KEY;

@Named
public class GoogleCloudDatastoreFactory extends AbstractGoogleClientFactory
{

  Datastore create(final BlobStoreConfiguration configuration) throws Exception {
    DatastoreOptions.Builder builder = DatastoreOptions.newBuilder().setTransportOptions(transportOptions());

    String credentialFile = configuration.attributes(CONFIG_KEY).get(CREDENTIAL_FILE_KEY, String.class);
    if (StringUtils.hasText(credentialFile)) {
      builder.setCredentials(ServiceAccountCredentials.fromStream(new FileInputStream(credentialFile)));
      builder.setProjectId(getProjectId(credentialFile));
    }

    return builder.build().getService();
  }
}
