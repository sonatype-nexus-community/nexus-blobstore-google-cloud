#!/usr/bin/env bash
# Utility script to automate adopting a new upstream version of Nexus Repository

# Fail script with error if any command doesn't succeed
set -e
trap "{ echo '[FAIL] Reverting changes due to failure...'; git restore .; }" ERR

# Bump project.version to the next minor increment
echo "Incrementing project.version..."
mvn io.github.q3769:semver-maven-plugin:20221011.0.7:increment-minor -Dsnapshot=true -DprocessModule=true -q

# The parent of this project (nexus-plugins:pom) is part of Nexus Repository. Update the parent to the "next" version.
# Most of the project dependencies will be updated to match, as versions come from the parent.
echo "Updating parent version..."
mvn versions:update-parent -U -q

# We have a property called "nxrm-version" used to indicate the version for Nexus Repository dependencies not inherited
# from the parent. Bump that to the next version to match the parent.
echo "Updating 'nxrm-version' property..."
mvn versions:update-properties -DincludeProperties=nxrm-version -U -q

# keep the changes from the versions plugin (not a git commit)
echo "Cleaning up after the versions plugin..."
mvn versions:commit -q

# 4. Run a compile and unit test to confirm compatibility
echo "Running a compile and unit test to confirm compatibility..."
mvn clean test

# Extract the new plugin version from the POM
PLUGIN_VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:3.3.0:evaluate -Dexpression=project.version -q -DforceStdout)
echo "New plugin version is ${PLUGIN_VERSION}"

# Extract the new Nexus Repository version from the POM
NEXUS_VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:3.3.0:evaluate -Dexpression=project.parent.version -q -DforceStdout)
# Drop the last fragment of the version (e.g. 3.47.0-01 becomes 3.47.0)
NEXUS_VERSION_TRIMMED="${NEXUS_VERSION%???}"
echo "New Nexus Repository version is ${NEXUS_VERSION_TRIMMED}"

# inline edit the ARG NEXUS_VERSION line in the development Dockerfile
sed -i '' "s/ARG NEXUS_VERSION=.*/ARG NEXUS_VERSION=${NEXUS_VERSION_TRIMMED}/g" nexus-blobstore-google-cloud/Dockerfile
# inline edit the ARG PLUGIN_VERSION line in the development Dockerfile
sed -i '' "s/ARG PLUGIN_VERSION=.*/ARG PLUGIN_VERSION=${PLUGIN_VERSION}/g" nexus-blobstore-google-cloud/Dockerfile
echo "Development Dockerfile updated for new versions"

git commit -am "chore: update to Nexus Repository ${NEXUS_VERSION_TRIMMED}"
echo "[SUCCESS] Dependency update committed."
