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

import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.common.collect.Lists;

import static org.sonatype.nexus.blobstore.gcloud.internal.DatastoreKeyHierarchy.NAMESPACE_PREFIX;
import static org.sonatype.nexus.blobstore.gcloud.internal.DatastoreKeyHierarchy.NXRM_ROOT;
import static org.sonatype.nexus.blobstore.gcloud.internal.Namespace.safe;

/**
 * Index of soft-deleted {@link BlobId}s, stored in Google Datastore.
 **
 * The key ancestry looks like:
 * <pre>
 [namespace: blobstore-/BlobStoreConfiguration.getName()/]
 kind=Sonatype,name=Nexus Repository Manager
 --> kind=DeletedBlobs
 * </pre>
 *
 * This key ancestry is intended to support separation of deleted blobs for multiple google cloud blobstore instances.
 */
class DeletedBlobIndex
    extends ComponentSupport
{
  private Datastore gcsDatastore;

  private KeyFactory deletedBlobsKeyFactory;

  private final String namespace;

  private static final String DELETED_BLOBS = "DeletedBlobs";

  static final Integer WARN_LIMIT = 1000;

  DeletedBlobIndex(final GoogleCloudDatastoreFactory factory, final BlobStoreConfiguration blobStoreConfiguration)
      throws Exception {
    this.gcsDatastore = factory.create(blobStoreConfiguration);
    this.namespace = NAMESPACE_PREFIX + safe(blobStoreConfiguration.getName());
    // this key factory will be used to add/remove blobIds from within the DELETED_BLOBS kind
    this.deletedBlobsKeyFactory = gcsDatastore.newKeyFactory()
        .addAncestors(NXRM_ROOT)
        .setNamespace(namespace)
        .setKind(DELETED_BLOBS);
  }

  /**
   * Add a {@link BlobId} to the index.
   */
  void add(final BlobId blobId) {
    Key key = deletedBlobsKeyFactory.newKey(blobId.asUniqueString());
    Entity entity = Entity.newBuilder(key)
        .build();
    gcsDatastore.put(entity);
  }

  /**
   * Remove a {@link BlobId} from the index
   */
  void remove(final BlobId blobId) {
    gcsDatastore.delete(deletedBlobsKeyFactory.newKey(blobId.asUniqueString()));
  }

  /**
   * Removes all deleted blobs tracked in this index.
   *
   */
  void removeData() {
    log.warn("removing all entries in the index of soft-deleted blobs...");
    Query<Entity> query = Query.newEntityQueryBuilder()
        .setNamespace(namespace)
        .setKind(DELETED_BLOBS)
        .build();
    QueryResults<Entity> results = gcsDatastore.run(query);
    List<Key> keys = StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(results, Spliterator.ORDERED),
        false).map(entity -> entity.getKey()).collect(Collectors.toList());

    // datastore has a hard limit of 500 keys in a single delete
    List<List<Key>> partitions = Lists.partition(keys, 500);
    partitions.stream().forEach(partition -> gcsDatastore.delete(partition.toArray(new Key[partition.size()])) );

    log.warn("deleted {} blobIds from the soft-deleted blob index", keys.size());
  }
  /**
   * @return a (finite) {@link Stream} of {@link BlobId}s that have been soft-deleted.
   */
  Stream<BlobId> getContents() {
    Query<Entity> query = Query.newEntityQueryBuilder()
        .setKind(DELETED_BLOBS)
        .setNamespace(namespace)
        .setLimit(WARN_LIMIT)
        .build();
    QueryResults<Entity> results = gcsDatastore.run(query);
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(results, Spliterator.ORDERED),
        false).map(entity -> new BlobId(entity.getKey().getName()));
  }
}
