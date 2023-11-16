<!--

    Sonatype Nexus (TM) Open Source Version
    Copyright (c) 2017-present Sonatype, Inc.
    All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.

    This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
    which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.

    Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
    of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
    Eclipse Foundation. All other trademarks are the property of their respective owners.

-->
# Reducing use of non-free Datastore operations

This design document identifies our goal to use as many Small Operations as possible; it amends the original 
[Datastore for Blobstore metadata design](datastore_for_blobstore_metadata.md).

## Definitions

[Google Cloud Datastore Pricing Reference](https://cloud.google.com/datastore/pricing)

Datastore reads, writes, and deletes have different costs, typically some multiple of 10,000 per day
for $0.07-$0.33 depending on operation and region.

Small operations are free across all regions, and [include](https://cloud.google.com/datastore/pricing#small_operations):

> * Calls to allocate IDs.
> * Keys-only queries. A keys-only query is counted as a single entity read for the query itself. The individual results are counted as small operations.
> * Projection queries that do not use the distinct on clause. This type of query is counted as a single entity read for the query itself. The individual results are counted as small operations.

## Context

In [#83](https://github.com/sonatype-nexus-community/nexus-blobstore-google-cloud/issues/83) we learned
that if we aren't careful, datastore reads can stack up quickly and present significant financial cost.

## Strategy

[#84](https://github.com/sonatype-nexus-community/nexus-blobstore-google-cloud/pull/84) demonstrates the desired approach.

### DeletedBlobsIndex

The `Stream<BlobId> getContents()` method presents the largest risk of heavy Datastore reads and high financial cost. 
The number of entities for the DeletedBlobIndex kind can grow unbounded. The 
[Compact Blobstore Task](https://help.sonatype.com/repomanager3/system-configuration/tasks) is designed to read this 
index and delete the blobs associated with the ids in the index at a preferable time for maintenance. This task is not 
setup by default, so it is common to see NXRM instances with larger lists of these entities.

Since the DeletedBlobIndex kind of entities only store data in the Key (the BlobId), it is critical that we avoid Entity
queries and use Key only queries for this data.

### ShardedCounterMetricsStore

The entities in the ShardedCounterMetricsStore contain two numeric fields. The `BlobStoreMetrics getMetrics()` method
can be invoked frequently by the User Interface and healthchecks. The implementation of this method prefers executing
Projection Queries (which are Small Operations, and free) to pull the individual fields rather than full entity reads 
(which have financial cost).

## Future uses

* Key only queries are ideal, so look to limit entities to just storing the key as it's data and no additional fields.
* Avoid full Entity reads if at all possible, prefer even multiple projection queries.
