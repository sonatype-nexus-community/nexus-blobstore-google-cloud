#!/usr/bin/env bash

# Extract the new Nexus Repository version from the POM
NEXUS_VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:3.3.0:evaluate -Dexpression=project.parent.version -q -DforceStdout)
# Drop the last fragment of the version (e.g. 3.47.0-01 becomes 3.47.0)
NEXUS_VERSION_TRIMMED="${NEXUS_VERSION%???}"
echo "New Nexus Repository version is ${NEXUS_VERSION_TRIMMED}"

# inline edit the ARG NEXUS_VERSION line in the development Dockerfile
sed -i '' "s/ARG NEXUS_VERSION=.*/ARG NEXUS_VERSION=${NEXUS_VERSION_TRIMMED}/g" Dockerfile

PLUGIN_VERSION=${NEXUS_VERSION_TRIMMED/3\./0\.}
echo "New Plugin version is ${PLUGIN_VERSION}"

sed -i '' "s/ARG PLUGIN_VERSION=.*/ARG PLUGIN_VERSION=${PLUGIN_VERSION}/g" Dockerfile
