FROM maven:3.6-jdk-8-alpine as nexus-blobstore-google-cloud
WORKDIR /build
COPY . /build/.
RUN mvn clean package

FROM sonatype/nexus3:3.15.2
ADD install-plugin.sh /opt/plugins/nexus-blobstore-google-cloud/
COPY --from=nexus-blobstore-google-cloud /build/target/ /opt/plugins/nexus-blobstore-google-cloud/target/
COPY --from=nexus-blobstore-google-cloud /build/pom.xml /opt/plugins/nexus-blobstore-google-cloud/

USER root

RUN cd /opt/plugins/nexus-blobstore-google-cloud/ && \
    chmod +x install-plugin.sh && \
    ./install-plugin.sh /opt/sonatype/nexus/ && \
    rm -rf /opt/plugins/nexus-blobstore-google-cloud/

RUN chown -R nexus:nexus /opt/sonatype/

USER nexus
