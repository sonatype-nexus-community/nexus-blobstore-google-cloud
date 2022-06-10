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
package org.sonatype.nexus.blobstore.gcloud.internal

import org.apache.commons.lang3.time.StopWatch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sonatype.nexus.blobstore.MockBlobStoreConfiguration
import org.sonatype.nexus.blobstore.api.BlobId
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration
import spock.lang.Specification

import java.util.stream.Stream

import static java.util.UUID.randomUUID

class DeletedBlobIndexIT extends Specification {

    static final String uid = randomUUID().toString().substring(0,6)
    static final Logger log = LoggerFactory.getLogger(DeletedBlobIndexIT.class)

    static final BlobStoreConfiguration config = new MockBlobStoreConfiguration()
    GoogleCloudDatastoreFactory datastoreFactory = new GoogleCloudDatastoreFactory()

    DeletedBlobIndex deletedBlobIndex

    def setupSpec() {
        config.name = "DeletedBlobIndexIT-${uid}"
        log.debug("deletedBlobIndex namespace: $uid")
        config.attributes = [
                'google cloud storage': [
                        credentialFilePath: this.getClass().getResource('/gce-credentials.json').getFile()
                ]
        ]
    }

    def setup() {
        deletedBlobIndex = new DeletedBlobIndex(datastoreFactory, config)
        deletedBlobIndex.initialize()
    }

    def cleanup() {
        deletedBlobIndex.removeData()
    }

    def 'control experiment'() {
        given:
            def size = 100
            List<BlobId> blobIds = new ArrayList<>()
            StopWatch watch = StopWatch.createStarted()
            for(int i = 0; i < size; i++) {
                BlobId blobId = new BlobId(randomUUID().toString())
                blobIds.add(blobId)
                deletedBlobIndex.add(blobId)
            }
            watch.stop()
            log.debug("all blobIds created and added in $watch")

        when:
            watch.reset()
            watch.start()
            Stream<BlobId> stream = deletedBlobIndex.getContents()

        then:
            watch.stop()
            log.debug("getContents() complete in $watch...")
            watch.reset()
            watch.start()
            stream.count() == size
            watch.stop()
            log.debug("count() complete in $watch...")
    }
}
