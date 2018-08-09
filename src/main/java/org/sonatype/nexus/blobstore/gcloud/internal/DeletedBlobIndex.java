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

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;

/**
 * Index of soft-deleted {@link BlobId}s, stored in Google Datastore.
 */
class DeletedBlobIndex
{

  private Datastore gcsDatastore;

  private KeyFactory deletedBlobsKeyFactory;

  private String deletedBlobsKeyKind;

  DeletedBlobIndex(final GoogleCloudDatastoreFactory factory, final BlobStoreConfiguration blobStoreConfiguration) {
    this.gcsDatastore = factory.create(blobStoreConfiguration);
    this.deletedBlobsKeyKind = "NXRM-DeletedBlobs-" + blobStoreConfiguration.getName();
    this.deletedBlobsKeyFactory = gcsDatastore.newKeyFactory().setKind(deletedBlobsKeyKind);
  }

  /**
   * Add a {@link BlobId} to the index.
   */
  void add(final BlobId blobId) {
    Key key = deletedBlobsKeyFactory.newKey(blobId.asUniqueString());
    Entity entity = Entity.newBuilder(key).build();
    gcsDatastore.put(entity);
  }

  /**
   * Remove a {@link BlobId} from the index
   */
  void remove(final BlobId blobId) {
    gcsDatastore.delete(deletedBlobsKeyFactory.newKey(blobId.asUniqueString()));
  }

  /**
   * @return a (finite) {@link Stream} of {@link BlobId}s that have been soft-deleted.
   */
  Stream<BlobId> getContents() {
    Query<Entity> query = Query.newEntityQueryBuilder()
        .setKind(this.deletedBlobsKeyKind)
        .setLimit(10000)
        .build();
    QueryResults<Entity> results = gcsDatastore.run(query);
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(results, Spliterator.ORDERED),
        false).map(entity -> new BlobId(entity.getKey().getName()));
  }
}
