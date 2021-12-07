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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.cloud.datastore.*;
import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.gcloud.GoogleCloudProjectException;

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
 *
 * This index has one configurable property: nexus.gcs.deletedBlobIndex.contentQueryLimit.
 * This property affects the behavior of {@link #getContents()}.
 */
class DeletedBlobIndex
    extends ComponentSupport
{
  private Datastore gcsDatastore;

  private KeyFactory deletedBlobsKeyFactory;

  private final String namespace;

  private static final String DELETED_BLOBS = "DeletedBlobs";

  static final Integer WARN_LIMIT = 1000;

  static final int DEFAULT_CONTENT_QUERY_LIMIT = 100_000;

  private final int contentQueryLimit;

  DeletedBlobIndex(final GoogleCloudDatastoreFactory factory, final BlobStoreConfiguration blobStoreConfiguration)
      throws Exception {
    this(factory, blobStoreConfiguration, DEFAULT_CONTENT_QUERY_LIMIT);
  }

  DeletedBlobIndex(final GoogleCloudDatastoreFactory factory, final BlobStoreConfiguration blobStoreConfiguration,
                   final int contentQueryLimit)
      throws Exception {
    this.gcsDatastore = factory.create(blobStoreConfiguration);
    this.namespace = NAMESPACE_PREFIX + safe(blobStoreConfiguration.getName());
    // this key factory will be used to add/remove blobIds from within the DELETED_BLOBS kind
    this.deletedBlobsKeyFactory = gcsDatastore.newKeyFactory()
        .addAncestors(NXRM_ROOT)
        .setNamespace(namespace)
        .setKind(DELETED_BLOBS);
    this.contentQueryLimit = contentQueryLimit;
  }

  int getContentQueryLimit() {
    return contentQueryLimit;
  }

  void initialize() {
    try {
      test();
    }
    catch (DatastoreException e) {
      throw new GoogleCloudProjectException("unable to write deleted blob metadata", e);
    }
  }

  void test() {
    BlobId sentinel = new BlobId("tmp$/sentinel");
    add(sentinel);
    remove(sentinel);
  }
  /**
   * Add a {@link BlobId} to the index.
   */
  void add(final BlobId blobId) {
    Key key = deletedBlobsKeyFactory.newKey(blobId.asUniqueString());
    Entity entity = Entity.newBuilder(key)
        .build();
    // document write
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
    Query<Key> query = Query.newKeyQueryBuilder()
        .setNamespace(namespace)
        .setKind(DELETED_BLOBS)
        .build();
    // small operation - key only
    QueryResults<Key> results = gcsDatastore.run(query);
    List<Key> keys = StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(results, Spliterator.ORDERED),
        false).collect(Collectors.toList());

    // datastore has a hard limit of 500 keys in a single delete
    List<List<Key>> partitions = Lists.partition(keys, 500);
    log.warn("keys has length of {}, split into {} partitions of 500", keys.size(), partitions.size());
    // document delete
    IntStream.range(0, partitions.size()).forEach(idx -> {
      List<Key> partition = partitions.get(idx);
      gcsDatastore.delete(partition.toArray(new Key[partition.size()]));
      log.debug("deleted partition " + idx);
    });

    log.warn("deleted {} blobIds from the soft-deleted blob index", keys.size());
  }
  /**
   * The mechanics of Google Firestore necessitates the use of key-only queries. As the index can grow unbounded,
   * and this method is used in healthchecks, a reads of all full Entities from this Kind can quickly pass free daily
   * limits.
   *
   * This implementation out of necessity uses key-only queries to fall within Small Operations, which are free.
   * The number of elements returned has an upper-bound of {@link #getContentQueryLimit()}. There are no guarantees
   * on the order of elements returned.
   *
   * @return a (finite) {@link Stream} of {@link BlobId}s that have been soft-deleted.
   */
  Stream<BlobId> getContents() {
    Query<Key> query = Query.newKeyQueryBuilder()
        .setKind(DELETED_BLOBS)
        .setNamespace(namespace)
        .setLimit(contentQueryLimit)
        .build();
    // small operation - key only query
    QueryResults<Key> results = gcsDatastore.run(query);
    return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(results, Spliterator.ORDERED),
        false).map(key -> new BlobId(key.getName()));
  }
}
