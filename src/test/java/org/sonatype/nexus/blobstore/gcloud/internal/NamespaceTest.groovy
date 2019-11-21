/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2017-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype
 * .com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License
 * Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are
 * trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.blobstore.gcloud.internal

import spock.lang.Specification
import spock.lang.Unroll

import static org.sonatype.nexus.blobstore.gcloud.internal.Namespace.namespaceSafe

class NamespaceTest
    extends Specification
{

  @Unroll
  def "it will convert namespaces when containing illegal characters"() {
    expect:
      namespaceSafe(prefix, key) == expected
    where:
      prefix      | key      | expected
      ''          | ''       | ''
      'foo'       | 'bar'    | 'foobar'
      'abc123ABC' | '._-'    | 'abc123ABC._-'
      'fo oo'     | 'bar'    | 'c859195b-5a43-3ed8-a454-d3451a2a3daf'
      'fo oo'     | 'ba ar'  | 'bb0dbdb5-3865-377e-ab2d-bec2622cac0f'
      'foo$'      | 'bar$'   | '1502c3f6-1364-3d02-af8e-b7f5b6605e23'
      'x' * 51    | 'y' * 50 | '5751745e-646f-37c8-8caa-8f0b54d64db5'
  }
}
