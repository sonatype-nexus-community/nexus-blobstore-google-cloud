# declaration of NEXUS_VERSION must appear before first FROM command
# see: https://docs.docker.com/engine/reference/builder/#understand-how-arg-and-from-interact
ARG NEXUS_VERSION=latest

FROM sonatype/nexus3:$NEXUS_VERSION

ARG PLUGIN_VERSION=0.11.0
ARG BUNDLE_NAME=nexus-blobstore-google-cloud-${PLUGIN_VERSION}.kar
ARG KAR_URL=https://repository.sonatype.org/service/local/repositories/releases/content/org/sonatype/nexus/plugins/nexus-blobstore-google-cloud/${PLUGIN_VERSION}/${BUNDLE_NAME}
ADD --chown=nexus:nexus ${KAR_URL} /opt/sonatype/nexus/deploy

USER nexus
