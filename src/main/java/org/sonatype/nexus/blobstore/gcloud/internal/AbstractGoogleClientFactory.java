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

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.ProxySelector;

import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.cloud.TransportOptions;
import com.google.cloud.http.HttpTransportOptions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

/**
 * Abstract supertype for Factory classes that generate Google Clients (for Storage, Datastore, etc).
 */
public abstract class AbstractGoogleClientFactory
{
  /**
   * Fixed keep-alive for HTTP connections of 1 minute.
   */
  public static final long KEEP_ALIVE_DURATION = 60_000L;

  /**
   * Provide a {@link TransportOptions} backed by Apache HTTP Client.
   *
   * @see ApacheHttpTransport
   * @return customized {@link TransportOptions} to use for our Google client instances
   */
  TransportOptions transportOptions() {
    return HttpTransportOptions.newBuilder()
        .setHttpTransportFactory(() -> new ApacheHttpTransport(newHttpClient()))
        .build();
  }

  HttpClient newHttpClient() {
    return newDefaultHttpClient(
        SSLSocketFactory.getSocketFactory(), newDefaultHttpParams(), ProxySelector.getDefault());
  }

  /**
   * Replicates default connection and protocol parameters used within
   * {@link ApacheHttpTransport#newDefaultHttpClient()} with one exception:
   *
   * Stale checking is enabled.
   */
  HttpParams newDefaultHttpParams() {
    HttpParams params = new BasicHttpParams();
    HttpConnectionParams.setStaleCheckingEnabled(params, true);
    HttpConnectionParams.setSocketBufferSize(params, 8192);
    ConnManagerParams.setMaxTotalConnections(params, 200);
    ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(20));
    return params;
  }

  /**
   * Replicates {@link ApacheHttpTransport#newDefaultHttpClient()} with one exception:
   *
   * 1 retry is allowed.
   *
   * @see DefaultHttpRequestRetryHandler
   */
  DefaultHttpClient newDefaultHttpClient(
      SSLSocketFactory socketFactory, HttpParams params, ProxySelector proxySelector) {
    SchemeRegistry registry = new SchemeRegistry();
    registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
    registry.register(new Scheme("https", socketFactory, 443));
    ClientConnectionManager connectionManager = new ThreadSafeClientConnManager(params, registry);
    DefaultHttpClient defaultHttpClient = new DefaultHttpClient(connectionManager, params);
    // retry only once
    defaultHttpClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(1, true));
    if (proxySelector != null) {
      defaultHttpClient.setRoutePlanner(new ProxySelectorRoutePlanner(registry, proxySelector));
    }
    defaultHttpClient.setKeepAliveStrategy((response, context) -> KEEP_ALIVE_DURATION);
    return defaultHttpClient;
  }

  /**
   * Utility method to help extract project_id field from the credential file.
   *
   * @param credentialFile the absolute path to the Google Cloud credential file
   * @throws IOException
   */
  String getProjectId(String credentialFile) throws IOException {
    try (Reader reader = new FileReader(credentialFile)) {
      JsonObject credentialJsonObject = new JsonParser().parse(reader).getAsJsonObject();
      return credentialJsonObject.get("project_id").getAsString();
    }
  }
}
