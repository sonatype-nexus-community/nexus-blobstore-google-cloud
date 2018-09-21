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
package org.sonatype.nexus.blobstore.gcloud.internal.attributes;

import java.util.Map;

import javax.annotation.Nullable;

import org.sonatype.nexus.blobstore.api.BlobAttributes;
import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobMetrics;

/**
 * Used internally by {@link org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore} to encapsulate
 * maintenance of {@link BlobAttributes}.
 */
public interface BlobAttributesDao
{
  @Nullable
  BlobAttributes getAttributes(BlobId blobId);

  void deleteAttributes(BlobId blobId);

  void markDeleted(BlobId blobId, String reason);

  void undelete(BlobId blobId);

  BlobAttributes storeAttributes(BlobId blobId, BlobAttributes blobAttributes);

  BlobAttributes storeAttributes(BlobId blobId, Map<String, String> headers, BlobMetrics metrics);
}
