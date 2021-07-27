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

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.common.collect.NestedAttributesMap;

import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.TYPE;

public class GoogleCloudBlobAttributesHelper
{
  public static final String CONFIG_KEY = TYPE.toLowerCase();

  private static final String OLD_BUCKET_KEY = "bucket";

  public static final String BUCKET_KEY = "bucketName";

  private static final String OLD_CREDENTIAL_FILE_KEY = "credential_file";

  public static final String CREDENTIAL_FILE_KEY = "credentialFilePath";

  private static final String OLD_LOCATION_KEY = "location";

  public static final String LOCATION_KEY = "region";

  public static void moveOldAttributes(final BlobStoreConfiguration blobStoreConfiguration) {
    NestedAttributesMap config = blobStoreConfiguration.attributes(CONFIG_KEY);

    if (!config.contains(BUCKET_KEY)) {
      config.set(BUCKET_KEY, config.get(OLD_BUCKET_KEY));
    }

    if (!config.contains(LOCATION_KEY)) {
      config.set(LOCATION_KEY, OLD_LOCATION_KEY);
    }

    if (!config.contains(CREDENTIAL_FILE_KEY)) {
      config.set(CREDENTIAL_FILE_KEY, OLD_CREDENTIAL_FILE_KEY);
    }

    config.remove(OLD_BUCKET_KEY);
    config.remove(OLD_LOCATION_KEY);
    config.remove(OLD_CREDENTIAL_FILE_KEY);
  }

  public static String requireConfiguredBucketName(final BlobStoreConfiguration blobStoreConfiguration) {
    return requireNewOrOldKey(blobStoreConfiguration, BUCKET_KEY, OLD_BUCKET_KEY);
  }

  public static String requireConfiguredRegion(final BlobStoreConfiguration blobStoreConfiguration) {
    return requireNewOrOldKey(blobStoreConfiguration, LOCATION_KEY, OLD_LOCATION_KEY);
  }

  public static String getConfiguredCredentialFileLocation(final BlobStoreConfiguration blobStoreConfiguration) {
    NestedAttributesMap config = blobStoreConfiguration.attributes(CONFIG_KEY);
    return config.get(CREDENTIAL_FILE_KEY, String.class, config.get(OLD_CREDENTIAL_FILE_KEY, String.class));
  }

  private static String requireNewOrOldKey(
      final BlobStoreConfiguration blobStoreConfiguration,
      final String newKey,
      final String oldKey)
  {
    NestedAttributesMap config = blobStoreConfiguration.attributes(CONFIG_KEY);
    if (config.contains(newKey)) {
      return config.get(newKey, String.class);
    }
    else if (config.contains(oldKey)) {
      return config.require(oldKey, String.class);
    }
    else {
      throw new IllegalStateException("Missing attribute " + newKey);
    }
  }
}
