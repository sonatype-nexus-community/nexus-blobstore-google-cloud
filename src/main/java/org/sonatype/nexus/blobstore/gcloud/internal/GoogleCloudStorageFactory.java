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

import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.TransportOptions;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.shiro.util.StringUtils;

import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.CONFIG_KEY;
import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.CREDENTIAL_FILE_KEY;

@Named
public class GoogleCloudStorageFactory
{

  Storage create(final BlobStoreConfiguration configuration) throws Exception {
    StorageOptions.Builder builder = StorageOptions.newBuilder().setTransportOptions(transportOptions());

    String credentialFile = configuration.attributes(CONFIG_KEY).get(CREDENTIAL_FILE_KEY, String.class);
    if (StringUtils.hasText(credentialFile)) {
       builder.setCredentials(ServiceAccountCredentials.fromStream(new FileInputStream(credentialFile)));
    }

    return builder.build().getService();
  }

  /**
   * This method overrides the default {@link com.google.auth.http.HttpTransportFactory} with the Apache HTTP Client
   * backed implementation. In addition, it modifies the {@link HttpClient} used internally to use a
   * {@link PoolingClientConnectionManager}.
   *
   * Note: at time of writing, this method uses deprecated classes that have been replaced in HttpClient with
   * {@link HttpClientBuilder}. We cannot use {@link HttpClientBuilder} currently because of a problem with the
   * Google Cloud Storage library's {@link ApacheHttpTransport} constructor; the {@link HttpClient} instance
   * returned by {@link HttpClientBuilder#build()} throws an {@link UnsupportedOperationException} for
   * {@link HttpClient#getParams()}.
   *
   * @see PoolingHttpClientConnectionManager
   * @see HttpClientBuilder
   * @return customized {@link TransportOptions} to use for our {@link Storage} client instance
   */
  TransportOptions transportOptions() {
    // replicate default connection and protocol parameters used within {@link ApacheHttpTransport}
    PoolingClientConnectionManager connManager = new PoolingClientConnectionManager();
    connManager.setDefaultMaxPerRoute(20);
    connManager.setMaxTotal(200);
    BasicHttpParams params = new BasicHttpParams();
    params.setParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false);
    params.setParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8192);
    DefaultHttpClient client = new DefaultHttpClient(connManager, params);

    return HttpTransportOptions.newBuilder()
      .setHttpTransportFactory(() -> new ApacheHttpTransport(client))
      .build();
  }
}
