Nexus Repository S3 Blobstores
==============================

[![Join the chat at https://gitter.im/sonatype/nexus-developers](https://badges.gitter.im/sonatype/nexus-developers.svg)](https://gitter.im/sonatype/nexus-developers?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This project adds [Google Cloud Object Storage](https://cloud.google.com/storage/) backed blobstores to Sonatype Nexus 
Repository 3.  It allows Nexus Repository to store the components and assets in Google Cloud instead of a
local filesystem.

Requirements
------------

* [Apache Maven 3.3.3+](https://maven.apache.org/install.html)
* [Java 8+](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* Network access to https://repository.sonatype.org/content/groups/sonatype-public-grid

Also, there is a good amount of information available at [Bundle Development Overview](https://help.sonatype.com/display/NXRM3/Bundle+Development#BundleDevelopment-BundleDevelopmentOverview)

Building
--------

To build the project and generate the bundle use Maven

    mvn clean install

If everything checks out, the nexus-blobstore-s3 bundle  should be available in the `target` folder


Installing
----------

See `install.sh`.  This copies the nexus-blobstore-google-cloud jar file to the
right place and updates the configuration files.  Use at your own
risk.

Alternatively, copy nexus-blobstore-google-cloud-*.jar and the Google Cloud bundle
jar into the nexus/deploy subdirectory.

Start the bundle from the Nexus Repository console:

```
bundle:list | grep nexus-blobstore-google-cloud
bundle:start <bundleNumber>
```

Configuration
-------------

Log in as admin and create a new blobstore, selecting 'Google Cloud Storage' as the type.
