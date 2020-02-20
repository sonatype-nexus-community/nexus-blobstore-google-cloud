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

import java.util.regex.Pattern;

import static java.util.UUID.nameUUIDFromBytes;

/**
 * Creates a GCP friendly namespace by using a subset of allowed characters in the form of a UUID type 3 value
 */
public class Namespace
{

  private static final Pattern GCP_NAMESPACE = Pattern.compile("^[0-9A-Za-z._\\-]{0,100}$");

  private Namespace() {
  }

  public static String safe(final String key) {
    if (GCP_NAMESPACE.matcher(key).matches()) {
      return key;
    }
    return nameUUIDFromBytes(key.getBytes()).toString();
  }
}
