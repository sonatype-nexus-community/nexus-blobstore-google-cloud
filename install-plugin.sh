#!/usr/bin/env bash

usage() {
cat << EOM
Usage:

  sh ./install-plugin.sh path/to/nxrm/install

 The path argument is required

Examples:
sh ./install-plugin.sh ../nexus-public/target/nexus-professional-3.13.0-SNAPSHOT
EOM
}

if [[ $# -ne 1 ]] ; then
  usage
  exit 1
fi
nxrmPath=$1

if [ ! -d "$nxrmPath/system" ] ; then
  >&2 echo "system folder does not exist under $nxrmPath, check that your path argument is the root of an NXRM 3 install."
  exit 1
fi

if [ ! -f "target/feature/feature.xml" ] ; then
  >&2 echo "Make sure you build the plugin first with 'mvn clean install'."
  exit 1
fi

pluginVersion=`xmllint --xpath "//*[local-name()='project']/*[local-name()='version']/text()" pom.xml`
pluginDir=$nxrmPath/system/org/sonatype/nexus/plugins/nexus-blobstore-google-cloud/$pluginVersion

set -e

mkdir -p $pluginDir
cp target/feature/feature.xml $pluginDir/nexus-blobstore-google-cloud-$pluginVersion-features.xml
cp target/nexus-blobstore-google-cloud-*.jar $pluginDir
echo "Plugin jar and feature deployed to $pluginDir..."

sed -i $nxrmPath/etc/karaf/org.ops4j.pax.url.mvn.cfg \
  -e "s/^org.ops4j.pax.url.mvn.repositories=/org.ops4j.pax.url.mvn.repositories=https:\/\/repo1.maven.org\/maven2@id=central/"

sed -i $nxrmPath/etc/karaf/org.apache.karaf.features.cfg \
  -e "/^featuresRepositories/ s#=#= mvn:org.sonatype.nexus.plugins/nexus-blobstore-google-cloud/$pluginVersion/xml/features,#"
echo "Container configuration deployed..."

echo "TODO Manual edits to $nxrmPath/system/org/sonatype/nexus/assemblies/nexus-core-feature/${NEXUS_VERSION}/nexus-core-feature-${NEXUS_VERSION}-features.xml still required, see README."
