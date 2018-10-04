package org.sonatype.nexus.blobstore.gcloud.internal.attributes

import org.sonatype.nexus.blobstore.BlobIdLocationResolver
import org.sonatype.nexus.blobstore.DefaultBlobIdLocationResolver
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration
import org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudStorageFactory

import com.google.cloud.storage.Bucket
import spock.lang.Specification

class PropertiesFileBlobAttributesDaoIT
  extends Specification
{
  BlobIdLocationResolver locationResolver = new DefaultBlobIdLocationResolver()

  final static GoogleCloudStorageFactory storageFactory = new GoogleCloudStorageFactory()

  PropertiesFileBlobAttributesDao attributesDao

  final static BlobStoreConfiguration config = new BlobStoreConfiguration()

  static Bucket bucket

  def setupSpec() {
    // create storage and bucket
  }

  def setup() {
    config.attributes = [
        'google cloud storage': [
            credential_file: this.getClass().getResource('/gce-credentials.json').getFile()
        ]
    ]
    config.name = 'PropertiesFileBlobAttributesDaoIT'

    def storage = storageFactory.create(config)

    attributesDao = new PropertiesFileBlobAttributesDao(locationResolver, bucket, storage, config, bucketName)
  }
}
