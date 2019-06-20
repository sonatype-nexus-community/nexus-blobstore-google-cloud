FROM sonatype/nexus3:3.16.2

ARG PLUGIN_VERSION=0.6.1
ARG BUNDLE_NAME=nexus-blobstore-google-cloud-${PLUGIN_VERSION}-bundle.kar
ARG KAR_URL=https://repository.sonatype.org/service/local/repositories/releases/content/org/sonatype/nexus/plugins/nexus-blobstore-google-cloud/${PLUGIN_VERSION}/${BUNDLE_NAME}
ADD --chown=nexus:nexus ${KAR_URL} /opt/sonatype/nexus/deploy

USER nexus
