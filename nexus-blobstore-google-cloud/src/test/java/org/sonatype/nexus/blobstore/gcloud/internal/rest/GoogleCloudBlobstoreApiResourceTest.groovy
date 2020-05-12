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
package org.sonatype.nexus.blobstore.gcloud.internal.rest

import org.sonatype.nexus.blobstore.MockBlobStoreConfiguration
import org.sonatype.nexus.blobstore.api.BlobStore
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration
import org.sonatype.nexus.blobstore.api.BlobStoreManager
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport
import org.sonatype.nexus.blobstore.quota.internal.SpaceUsedQuota

import spock.lang.Specification

class GoogleCloudBlobstoreApiResourceTest
  extends Specification
{
  static BlobStoreConfiguration config = new MockBlobStoreConfiguration()

  BlobStoreManager blobStoreManager = Mock()

  GoogleCloudBlobstoreApiResource api = new GoogleCloudBlobstoreApiResource(blobStoreManager)

  def setup() {
    config = makeConfig('apitest')
  }

  def "returns null for not found"() {
    expect:
      api.get('undefined') == null
  }

  def "returns config for existing store"() {
    given:
      BlobStore blobstore = Mock()
      blobstore.getBlobStoreConfiguration() >> config
      blobStoreManager.get('apitest') >> blobstore

    when:
      GoogleCloudBlobstoreApiModel model = api.get('apitest')

    then:
      model.name == 'apitest'
  }

  def "create prevents duplicates"() {
    given:
      BlobStore blobstore = Mock()
      blobstore.getBlobStoreConfiguration() >> config
      blobStoreManager.get('apitest') >> blobstore

    when:
      api.create(new GoogleCloudBlobstoreApiModel(makeConfig('apitest')))

    then:
      thrown(IllegalArgumentException)
  }

  def "create success"() {
    given:
      def createConf = makeConfig('createtest')
      def model = new GoogleCloudBlobstoreApiModel(createConf)

    when:
      GoogleCloudBlobstoreApiModel result = api.create(model)

    then:
      result.name == 'createtest'
  }

  def makeConfig(String name) {
    BlobStoreConfiguration result = new MockBlobStoreConfiguration()
    config.name = name
    config.attributes = [
        'google cloud storage': [
            bucket: 'bucketname',
            location: 'us-central1'
        ],
        (BlobStoreQuotaSupport.ROOT_KEY): [
            (BlobStoreQuotaSupport.TYPE_KEY): (SpaceUsedQuota.ID),
            (BlobStoreQuotaSupport.LIMIT_KEY): (512000L)
        ]
    ]
    return result
  }
}
