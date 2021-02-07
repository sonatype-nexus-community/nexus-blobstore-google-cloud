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
import org.sonatype.nexus.blobstore.file.FileBlobStore
import org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport
import org.sonatype.nexus.blobstore.quota.internal.SpaceUsedQuota
import org.sonatype.nexus.rest.WebApplicationMessageException

import spock.lang.Specification

class GoogleCloudBlobstoreApiResourceTest
  extends Specification
{
  static BlobStoreConfiguration config = new MockBlobStoreConfiguration()

  BlobStoreManager blobStoreManager = Mock()

  BlobStore blobstore = Mock()

  GoogleCloudBlobstoreApiResource api = new GoogleCloudBlobstoreApiResource(blobStoreManager)

  def setup() {
    config = makeConfig('apitest')

    blobstore.getBlobStoreConfiguration() >> config
    blobStoreManager.get('apitest') >> blobstore

    blobStoreManager.newConfiguration() >> new MockBlobStoreConfiguration()
  }

  def "returns null for not found"() {
    expect:
      api.get('undefined') == null
  }

  def "get returns config for existing store"() {
    when:
      GoogleCloudBlobstoreApiModel model = api.get('apitest')

    then:
      model.name == 'apitest'
  }

  def "get throws exception for non-google type"() {
    given:
      def fileconfig = makeConfig('file')
      fileconfig.type = FileBlobStore.TYPE
      BlobStore fileblobstore = Mock()
      fileblobstore.getBlobStoreConfiguration() >> fileconfig
      blobStoreManager.get('file') >> fileblobstore

    when:
      api.get('file')

    then:
      thrown(WebApplicationMessageException)
  }

  def "create prevents duplicates"() {
    when:
      api.create(new GoogleCloudBlobstoreApiModel(config))

    then:
      thrown(WebApplicationMessageException)
  }

  def "merge handles credential_file"() {
    given:
      GoogleCloudBlobstoreApiModel model = new GoogleCloudBlobstoreApiModel();
      model.setBucketName('bucketname')
      model.setName('blobstorename')
      model.setRegion('us-central1')
      model.setCredentialFilePath('/path/to/a/file')
      MockBlobStoreConfiguration config = new MockBlobStoreConfiguration()

    when:
      api.merge(config, model)

    then:
      config.attributes('google cloud storage').get('bucket') == model.bucketName
      config.attributes('google cloud storage').get('location') == model.region
      config.attributes('google cloud storage').get('credential_file') == model.credentialFilePath
      config.name == model.name
  }

  def makeConfig(String name) {
    MockBlobStoreConfiguration result = new MockBlobStoreConfiguration()
    result.name = name
    result.type = GoogleCloudBlobStore.TYPE
    result.attributes = [
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
