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
Integration Tests for Nexus Repository Manager Google Cloud Storage Blobstore
====

The integration tests for Nexus Repository Manager Google Cloud Storage Blobstore interact with
Google Cloud Storage and require credentials to be provided.

The integration tests look in this folder (`src/test/resource`) for a file named `gce-credentials.json`,
which is ignored by git. The [root README](../../../README.md) for this project describes how to
create this file and what roles/permissions are required.

See the [example file](.gce-credentials-example.json) in this directory.

Once configured, to run the tests execute the following at the root of this project:

    mvn verify