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

import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.cloud.TransportOptions;
import com.google.cloud.http.HttpTransportOptions;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.http.client.HttpClient;

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
    return ApacheHttpTransport.newDefaultHttpClientBuilder()
        .setMaxConnPerRoute(200)
        .setMaxConnTotal(200)
        .setKeepAliveStrategy((response, context) -> KEEP_ALIVE_DURATION)
        .build();
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
