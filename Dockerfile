FROM maven:3.6-jdk-8-alpine as nexus-blobstore-google-cloud
WORKDIR /build
COPY . /build/.
RUN mvn clean package

FROM sonatype/nexus3:3.16.2
ADD target/nexus-blobstore-google-cloud*.kar /opt/sonatype/nexus/deploy

USER nexus
