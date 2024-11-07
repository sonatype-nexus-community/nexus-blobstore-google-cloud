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
Nexus Repository Google Cloud Storage Blobstore
==============================

> ℹ️ As of 7th November 2024, this community project has [graduated](https://contribute.sonatype.com/docs/project-classification/) and is offered as part of Sonatype's commercial offerings - see [here](https://help.sonatype.com/en/configuring-blob-stores.html#google-cloud-blob-store) for full details.
>
> 🚧 This community project will receive no further updates or maintenance.


[![CircleCI Build Status](https://circleci.com/gh/sonatype-nexus-community/nexus-blobstore-google-cloud.svg?style=shield "CircleCI Build Status")](https://circleci.com/gh/sonatype-nexus-community/nexus-blobstore-google-cloud) [![Maven Central](https://img.shields.io/maven-central/v/org.sonatype.nexus.plugins/nexus-blobstore-google-cloud.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.sonatype.nexus.plugins%22%20AND%20a:%22nexus-blobstore-google-cloud%22) [![Join the chat at https://gitter.im/sonatype/nexus-developers](https://badges.gitter.im/sonatype/nexus-developers.svg)](https://gitter.im/sonatype/nexus-developers?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This project adds [Google Cloud Object Storage](https://cloud.google.com/storage/) backed blobstores to Sonatype Nexus 
Repository 3 and later.  It allows Nexus Repository to store the components and assets in Google Cloud instead of a
local filesystem.

This plugin also uses [Google Firestore in Datastore mode](https://cloud.google.com/datastore/docs) to store some 
metadata about the blobstore. The plugin prioritizes using [Small Operations](https://cloud.google.com/datastore/pricing),
which have no financial cost, but do use some limited paid operations (read, write, delete) in a cost effective manner. 

Which Version do I use?
-----------------------

For the best experience, you should upgrade your Nexus Repository Manager and Google Cloud Blobstore plugin to the latest versions.

1. Navigate to https://search.maven.org/artifact/org.sonatype.nexus.plugins/nexus-blobstore-google-cloud
2. Select the version that matches your Nexus Repository Manager version. Example: 0.39 of the plugin is intended for Repository Manager 3.39, 0.38 for 3.38, etc.
3. Download the corresponding `kar` archive.

# Deploying the Plugin

Google Cloud Services and IAM Roles
-----------------------------------

This plugin uses the following Google Cloud Platform services:

* [Google Cloud Storage](https://cloud.google.com/storage/) - for storing the content blobs
* [Google Cloud Firestore in Datastore mode](https://cloud.google.com/datastore/) - for storing blobstore metadata

Firestore usage is exclusively in Datastore mode; you must configure the project for your Repository Manager deployment
to use ["Firestore in Datastore mode"](https://cloud.google.com/firestore/docs/firestore-or-datastore).

To use this plugin (or execute the integration tests), you will need a service account with the following 
[scopes](https://developers.google.com/identity/protocols/oauth2/scopes):

* https://www.googleapis.com/auth/cloud-platform
* https://www.googleapis.com/auth/compute.readonly
* https://www.googleapis.com/auth/devstorage.read_write
* https://www.googleapis.com/auth/datastore

Optionally, add the following for Cloud Logging:

* https://www.googleapis.com/auth/logging.write

The blobstore will create the storage bucket with the ['Multi-Regional' storage class](https://cloud.google.com/storage/sla).

Google Cloud Storage Authentication
-----------------------------------

Per the [Google Cloud documentation](https://github.com/GoogleCloudPlatform/google-cloud-java#authentication):

1. [Generate a JSON Service Account key](https://cloud.google.com/storage/docs/authentication?hl=en#service_accounts) 
2. Store this file on the filesystem with appropriate permissions for the user running Nexus to read it.
3. (optional, but recommended) Set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable for the user running Nexus:

```
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/my/key.json
```
Installing
----------

After downloading the kar from Maven Central (links above), copy the kar file to the `deploy` directory in your Nexus 
Repository Manager install:

```bash
cp nexus-blobstore-google-cloud-*-bundle.kar /path/to/your/nxrm3/install/deploy
```

Configuration
-------------

A restart of Nexus Repository Manager is required to complete the installation process.

Log in as admin and create a new blobstore, selecting 'Google Cloud Storage' as the type.

If you did not set the environment variable in Step 3 above, specify the absolute path to the JSON Service Account key file.

# Contributing to Plugin Development

Contribution Guidelines
-----------------------

Go read [our contribution guidelines](/.github/CONTRIBUTING.md) to get a bit more familiar with how
we would like things to flow.

Requirements
------------

* [Apache Maven 3.3.3+](https://maven.apache.org/install.html)
* [Java 8+](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* Network access to https://repository.sonatype.org/content/groups/sonatype-public-grid

Also, there is a good amount of information available at [Bundle Development Overview](https://help.sonatype.com/display/NXRM3/Bundle+Development#BundleDevelopment-BundleDevelopmentOverview)

Building from Source
-------------------

To build the project and generate the bundle use Maven:

    mvn clean package
    
Optional: review the [additional documentation to configure and run integration tests](src/test/resources/README.md).

Running a local development instance
------------------------------------

A [docker-compose file](docker-compose.yml) is provided to ease setting up a local NXRM instance to test. This compose
file does reference an external secret. If your docker runtime does not support this capability, copy the provided file
as _docker-compose-local.yml_, and change the external line to a file that points directly to the IAM credentials.

### First run

A volume is needed to store your local instance files:

`docker volume create nexus3-data`

### Build & start

1. `docker build -t nexus3-google .`
2. `docker-compose up -d`

You can also use the [docker-compose file](docker-compose.yml) with docker service, like so:

```bash
docker secret create google_application_credentials /path/to/your/google/iam/key.json
docker stack deploy -c docker-compose.yml sonatype
```

(Using docker stack assumes you've built the container with `docker build -t nexus3-google .` or run `docker-compose up` at least once).

Last manual option: you can install the local development build in any NXRM install with:

```bash
cp target/*-bundle.kar /path/to/your/nxrm3/install/deploy
```

The Fine Print
--------------

It is worth noting that this is **NOT SUPPORTED** by Sonatype, and is a contribution of ours
to the open source community (read: you!)

Remember:

* Use this contribution at the risk tolerance that you have
* Do NOT file Sonatype support tickets related to Google Cloud support
* DO file issues here on GitHub, so that the community can pitch in

Phew, that was easier than I thought. Last but not least of all:

Have fun creating and using this plugin and the Nexus platform, we are glad to have you here!

Getting help
------------

Looking to contribute to our code but need some help? There's a few ways to get information:

* Chat with us on [Gitter](https://gitter.im/sonatype/nexus-developers)
* Check out the [Nexus3](http://stackoverflow.com/questions/tagged/nexus3) tag on Stack Overflow
* Check out the [Nexus Repository User List](https://groups.google.com/a/glists.sonatype.com/forum/?hl=en#!forum/nexus-users)
