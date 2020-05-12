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

import spock.lang.Specification
import spock.lang.Unroll

import static org.sonatype.nexus.blobstore.gcloud.internal.Namespace.safe

class NamespaceTest
    extends Specification
{

  @Unroll
  def "it will convert namespaces when containing illegal characters"() {
    expect:
      safe(key) == expected
    where:
      key            | expected
      ''             | ''
      'bar'          | 'bar'
      'abc123ABC._-' | 'abc123ABC._-'
      'ba ar'        | '3ad6c2a6-5ccf-341c-ac0b-c670504de4dc'
      'bar$'         | '08c15d2b-4a9f-3907-9763-4a0685b370ca'
      'y' * 101      | '905b9d97-c7bb-3192-b6dc-f7fed334f708'
  }
}
