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

import java.nio.channels.FileChannel

import com.google.cloud.storage.Blob
import com.google.cloud.storage.Bucket
import spock.lang.Specification

/**
 * Unit tests for {@link GoogleCloudPropertiesFile}.
 */
class GoogleCloudPropertiesFileTest
  extends Specification
{
  Bucket bucket = Mock()

  static String testProperties = 'propertyName = value\n'

  static File tempFile

  def setupSpec() {
    tempFile = File.createTempFile('gcloudtest', 'properties')
    tempFile << testProperties
  }

  def cleanupSpec() {
    tempFile.delete()
  }

  def setup() {
    Blob blob = Mock()
    blob.reader() >> new DelegatingReadChannel(FileChannel.open(tempFile.toPath()))
    bucket.get('mykey') >> blob
  }

  def "Load ingests properties from google cloud storage object"() {
    given:
      GoogleCloudPropertiesFile propertiesFile = new GoogleCloudPropertiesFile(bucket, 'mykey')

    when:
      propertiesFile.load()

    then:
      propertiesFile.getProperty('propertyName') == 'value'
  }

  def "Store writes properties to google cloud storage object"() {
    given: 'load existing properties'
      GoogleCloudPropertiesFile propertiesFile = new GoogleCloudPropertiesFile(bucket, 'mykey')
      propertiesFile.load()

    when: 'add a second property'
      propertiesFile.setProperty('testProperty', 'newValue')
      propertiesFile.store()

    then: 'expect to see 2 properties stored to google bucket'
      1 * bucket.create(_, _ as byte[]) >> { String bucketname, byte[] content, varargs ->
        Properties properties = new Properties()
        properties.load(new ByteArrayInputStream(content))

        assert properties.get('testProperty') == 'newValue'
        assert properties.get('propertyName') == 'value'
      }
  }
}
