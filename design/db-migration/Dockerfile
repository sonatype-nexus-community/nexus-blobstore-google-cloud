FROM openjdk:8-jre-alpine

RUN apk update
RUN apk add --no-cache curl

# we intend to run this container against a volume that is typically mounted for the sonatype/nexus3 image
# in order to not mess up permissions, we need this image to run with a similar user/group
# the gid of the 'nexus' group on sonatype/nexus3 image is 200. On alpine, gid 200 is a group named 'nofiles'
RUN adduser -G nofiles -u 200 -s /bin/false --home /opt/sonatype/nexus nexus -D

RUN mkdir -p /usr/local/bin

# https://download.sonatype.com/nexus/nxrm3-migrator/nexus-db-migrator-3.51.0-01.jar
ARG NEXUS_VERSION=3.51.0-01
ARG NEXUS_DOWNLOAD_URL=https://download.sonatype.com/nexus/nxrm3-migrator/nexus-db-migrator-${NEXUS_VERSION}.jar
ARG NEXUS_DOWNLOAD_SHA256_HASH=6cd48693f89c14e093934c08cc72c7aea337c40ea96ae48daeb7ebbd10821bef

WORKDIR /usr/local/bin

RUN curl -L ${NEXUS_DOWNLOAD_URL} --output nexus-db-migrator-${NEXUS_VERSION}.jar \
    && echo "${NEXUS_DOWNLOAD_SHA256_HASH}  nexus-db-migrator-${NEXUS_VERSION}.jar" > nexus-db-migrator-${NEXUS_VERSION}.jar.sha256 \
    && sha256sum -c nexus-db-migrator-${NEXUS_VERSION}.jar.sha256

USER nexus
WORKDIR /data/db

# TODO do we actually want the docker run command to run the migrator?
# For now, no. We would also need it to edit nexus.properties if successful
#CMD ["java", "-jar", "/usr/local/bin/nexus-db-migrator-*.jar", "-y", "--migration_type=h2"]
