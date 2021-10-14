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

import org.sonatype.nexus.blobstore.MockBlobStoreConfiguration
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport
import org.sonatype.nexus.blobstore.quota.internal.SpaceUsedQuota
import spock.lang.IgnoreIf
import spock.lang.Specification

class GoogleCloudStorageFactoryTest extends Specification
{
    private GoogleCloudStorageFactory factory = new GoogleCloudStorageFactory()

    def "create default" () {
        given:
            MockBlobStoreConfiguration config = makeConfig('default', null)
        expect:
            factory.create(config)
    }

    @IgnoreIf({ getClass().getResource('/gce-credentials.json') == null })
    def "successful create with valid file path"() {
        given:
            MockBlobStoreConfiguration config = makeConfig('with-valid-path', '/gce-credentials.json')
        expect:
            factory.create(config)
    }

    def "ignores invalid credential file path"() {
        given:
            MockBlobStoreConfiguration config = makeConfig('with-invalid-path', 'this-file-doesnt-exist')
        expect:
            factory.create(config)
    }

    def "ignores invalid credential file content"() {
        given:
            MockBlobStoreConfiguration config = makeConfig('invalid-content', '/.gce-credentials-example.json')
        when:
            factory.create(config)
        then:
            thrown(IOException)
    }

    def makeConfig(String name, String credentialFilePath) {
        MockBlobStoreConfiguration config = new MockBlobStoreConfiguration()
        def credentialUrl = null
        if (credentialFilePath != null) {
            credentialUrl = this.getClass().getResource(credentialFilePath)?.getFile()
        }
        config.name = name
        config.attributes = [
                'google cloud storage': [
                        bucketName: 'test-bucket-name',
                        region: 'us-central1',
                        credentialFilePath: credentialUrl
                ],
                (BlobStoreQuotaSupport.ROOT_KEY): [
                        (BlobStoreQuotaSupport.TYPE_KEY): (SpaceUsedQuota.ID),
                        (BlobStoreQuotaSupport.LIMIT_KEY): 512000L
                ]
        ]
        return config
    }
}
