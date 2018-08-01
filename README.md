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

[![Build Status](https://travis-ci.org/sonatype-nexus-community/nexus-blobstore-google-cloud.svg?branch=master)](https://travis-ci.org/sonatype-nexus-community/nexus-blobstore-google-cloud) [![Join the chat at https://gitter.im/sonatype/nexus-developers](https://badges.gitter.im/sonatype/nexus-developers.svg)](https://gitter.im/sonatype/nexus-developers?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This project adds [Google Cloud Object Storage](https://cloud.google.com/storage/) backed blobstores to Sonatype Nexus 
Repository 3.  It allows Nexus Repository to store the components and assets in Google Cloud instead of a
local filesystem.

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

Building
--------

To build the project and generate the bundle use Maven:

    mvn clean install

Installing
----------

After you have built the project, run the provided install script

```bash
sh ./install-plugin.sh path/to/your/nxrm3/install
```

Google Cloud Storage Permissions
--------------------------------

Next, you will need to create an account with appropriate [permissions](https://cloud.google.com/storage/docs/access-control/iam-roles).

Of the predefined account roles, `Storage Admin` will grant the plugin to create any Google Cloud Storage Buckets you 
require and administer all of the objects within, but it will also have access to manage any other Google Cloud Storage
Buckets associated with the project.

If you are using custom roles, the account will need:

1. (required) `storage.objects.*`
2. (required) `storage.buckets.get` 
3. or `storage.buckets.*`.

If you only provide permissions 1 and 2, you will have to create any Google Cloud Storage Buckets in advance of assigning
them to Nexus Repository Manager blob stores. The third option will allow the plugin to create buckets for you.

If you are creating the Google Cloud Storage Buckets in advance, you should use either the 'Multi-Regional' or 'Regional'
[storage class](https://cloud.google.com/storage/sla); 'Nearline' and 'Coldline' are not suitable for Nexus Repository Manager workloads.
If you let the plugin create the bucket, it will use the 'Multi-Regional' storage class.

Google Cloud Storage Authentication
-----------------------------------

Per the [Google Cloud documentation](https://github.com/GoogleCloudPlatform/google-cloud-java#authentication):

1. [Generate a JSON Service Account key](https://cloud.google.com/storage/docs/authentication?hl=en#service_accounts) 
2. Store this file on the filesystem with appropriate permissions for the user running Nexus to read it.
3. (optional, but recommended) Set the `GOOGLE_APPLICATION_CREDENTIALS` environment variable for the user running Nexus:

```
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/my/key.json

```

Configuration
-------------

A restart of Nexus Repository Manager is required to complete the installation process.

Log in as admin and create a new blobstore, selecting 'Google Cloud Storage' as the type.

If you did not set the environment variable in Step 3 above, specify the absolute path to the JSON Service Account key file.

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