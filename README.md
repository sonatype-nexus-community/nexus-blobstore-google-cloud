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

[![Maven Central](https://img.shields.io/maven-central/v/org.sonatype.nexus.plugins/nexus-blobstore-google-cloud.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.sonatype.nexus.plugins%22%20AND%20a:%22nexus-blobstore-google-cloud%22) [![Join the chat at https://gitter.im/sonatype/nexus-developers](https://badges.gitter.im/sonatype/nexus-developers.svg)](https://gitter.im/sonatype/nexus-developers?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This project adds [Google Cloud Object Storage](https://cloud.google.com/storage/) backed blobstores to Sonatype Nexus 
Repository 3 and later.  It allows Nexus Repository to store the components and assets in Google Cloud instead of a
local filesystem.

Which Version do I use?
-----------------------

For the best experience, you should upgrade your Nexus Repository Manager and Google Cloud Blobstore plugin to the latest versions.

| Nexus Repository Manager 3 Version | Google Cloud Storage Blobstore Version |
| ---------------------------------- |--------------------------------------- |
| 3.20                               | 0.10.0                                 |
| 3.19                               | 0.9.2                                  |
| 3.18                               | 0.8.0                                  |
| 3.17                               | 0.7.1                                  |
| 3.16                               | 0.6.1                                  |
| 3.15                               | 0.4.0                                  |
| 3.13, 3.14                         | 0.3.0                                  |
| 3.11, 3.12                         | 0.2.0                                  |

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

Google Cloud Services and IAM Roles
-----------------------------------

This plugin uses the following Google Cloud Platform services:

* [Google Cloud Storage](https://cloud.google.com/storage/) - for storing the content blobs
* [Google Cloud Firestore](https://cloud.google.com/firestore/) - for storing blobstore metadata

Firestore usage is exclusively in Datastore mode; you must configure the project for your Repository Manager deployment
to use ["Firestore in Datastore mode"](https://cloud.google.com/firestore/docs/firestore-or-datastore).

To use this plugin (or execute the integration tests), you will need an account with the following roles:

* [Storage Admin](https://cloud.google.com/storage/docs/access-control/iam-roles)
* [Cloud Datastore Owner](https://cloud.google.com/datastore/docs/access/iam)

The blobstore will create the storage bucket with the ['Multi-Regional' storage class](https://cloud.google.com/storage/sla).

Building the Source
-------------------

To build the project and generate the bundle use Maven:

    mvn clean package
    
Optional: review the [additional documentation to configure and run integration tests](src/test/resources/README.md).

Installing
----------

After you have built the project, copy the kar file to the `deploy` directory in your Nexus Repository Manager install:

```bash
cp target/*.kar /path/to/your/nxrm3/install/deploy
```

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

### New in 0.10 - Parallel Multipart Upload

In version 0.10.0 and later, the plugin now uploads content to the Google Cloud Storage bucket using multiple concurrent
threads. This can increase throughput from NXRM to the bucket by 50% depending on workload.

The `MultipartUploader` class that implements this feature uses a default of `5 MB` as a threshold for the size of
chunk of the stream to upload in parallel. 

* Content smaller than this size will just upload to the bucket on the request thread.
* Content greater than `5 MB` but less than `155 MB` (31 chunks * 5) 
* Content greater than `160 MB` will use the parallel chunked upload for the first `155 MB` but fall back to the original approach for the remaining content.

If you wish to adjust the chunk size, you can specify the desired size (in bytes) by adding the following 
to `nexus.properties` for your instance:

> nexus.gcs.multipartupload.chunksize=5242880

If you see the following `INFO` message in your log file, it is worth experimenting with incrementally higher values:

```
09:17:38.381 [main] INFO  o.s.n.b.g.internal.MultipartUploader - Upload for vol-01/chap-01/composeLimitTest/abcde123 
has hit Google Cloud Storage multipart-compose limit (N total times limit hit); consider increasing 
'nexus.gcs.multipartupload.chunksize' beyond current value of 5242880
```

**Caution:** setting this value arbitrarily high will exert heap memory pressure on your environment. Setting it lower
may result in increased overhead for small content. It is better to be slightly low and see this message infrequently
than to be too high and require a really large heap all the time.

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