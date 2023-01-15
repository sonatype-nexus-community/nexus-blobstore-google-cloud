# Dockerfile to support local development against snapshot builds
ARG NEXUS_VERSION=3.44.0

FROM sonatype/nexus3:$NEXUS_VERSION

ARG PLUGIN_VERSION=0.44.0-SNAPSHOT
ARG BUNDLE_NAME=nexus-blobstore-google-cloud-${PLUGIN_VERSION}.kar
COPY ./target/${BUNDLE_NAME} /opt/sonatype/nexus/deploy
RUN mkdir -p /opt/sonatype/sonatype-work/nexus3/etc

USER nexus
