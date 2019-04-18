#!/usr/bin/env bash

usage() {
cat << EOM
Usage:

  sh ./install-plugin.sh path/to/nxrm/install

 The path argument is required

Examples:
sh ./install-plugin.sh ../nexus-public/target/nexus-professional-3.16.1-02
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

plugin=nexus-blobstore-google-cloud
pluginVersion=`xmllint --xpath "//*[local-name()='project']/*[local-name()='version']/text()" pom.xml`
pluginDir=$nxrmPath/system/org/sonatype/nexus/plugins/$plugin/$pluginVersion

mkdir -p $pluginDir
cp target/feature/feature.xml $pluginDir/$plugin-$pluginVersion-features.xml
cp target/$plugin-*.jar $pluginDir
echo "Plugin jar and feature deployed to $pluginDir..."

sed -i $nxrmPath/etc/karaf/org.ops4j.pax.url.mvn.cfg \
  -e "s/^org.ops4j.pax.url.mvn.repositories=/org.ops4j.pax.url.mvn.repositories=https:\/\/repo1.maven.org\/maven2@id=central/"
# only insert if not already present
grep -q "$mvn:org.sonatype.nexus.plugins/$plugin/" $nxrmPath/etc/karaf/org.apache.karaf.features.cfg
if [ $? -ne 0 ]; then
  sed -i $nxrmPath/etc/karaf/org.apache.karaf.features.cfg \
    -e "/^featuresRepositories/ s#=#= mvn:org.sonatype.nexus.plugins/$plugin/$pluginVersion/xml/features,#"
fi
echo "Container configuration deployed..."

sed -i "/nexus-task-log-cleanup<\/feature>/ a <feature version=\"$pluginVersion\" prerequisite=\"false\" dependency=\"false\">$plugin</feature>" $nxrmPath/system/org/sonatype/nexus/assemblies/nexus-core-feature/*/nexus-core-feature-*-features.xml

echo "$nxrmPath/system/org/sonatype/nexus/assemblies/nexus-core-feature/<NEXUS_VERSION>/nexus-core-feature-<NEXUS_VERSION>-features.xml edited."
