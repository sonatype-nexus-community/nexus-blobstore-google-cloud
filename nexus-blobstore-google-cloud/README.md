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

# nexus-blobstore-google-cloud 

This module contains the actual plugin code. A separate Dockerfile and docker-compose file are provided here
to run snapshot builds locally for debug/development purposes.

## First run

Depends on the same secret being present as the docker-compose in the parent directory.
This instance does not use a volume because the data can be ephemeral.

1. `docker swarm init`
2. `docker secret create google_application_credentials /path/to/your/google/iam/key.json`

## Running

1. `mvn clean package`
2. `docker build -t nexus3-google-dev .`
3. `docker-compose up`
