#!/bin/bash -e

export GCLOUD_BLOBSTORE_VERSION=0.0.1-SNAPSHOT
export NEXUS_HOME=../nexus-professional-3.5.1-02
#export NEXUS_HOME=../nexus-internal/target/nexus-professional-3.6.0-SNAPSHOT

mkdir -p ${NEXUS_HOME}/system/org/sonatype/nexus/nexus-blobstore-google-cloud/${GCLOUD_BLOBSTORE_VERSION}/
cp target/nexus-blobstore-google-cloud-*.jar ${NEXUS_HOME}/system/org/sonatype/nexus/nexus-blobstore-google-cloud/${GCLOUD_BLOBSTORE_VERSION}/

sed -i.bak -e "/nexus-blobstore-file/a\\"$'\n'"<bundle>mvn:org.sonatype.nexus/nexus-blobstore-google-cloud/${GCLOUD_BLOBSTORE_VERSION}</bundle>" ${NEXUS_HOME}/system/org/sonatype/nexus/assemblies/nexus-base-feature/*/nexus-base-feature-*-features.xml
