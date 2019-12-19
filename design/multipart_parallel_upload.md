# Multipart Parallel Upload Design

This document is intended to capture the context around the component within this blobstore that provides 
Parallel Multipart Upload:

[MultipartUploader.java](../src/main/java/org/sonatype/nexus/blobstore/gcloud/internal/MultipartUploader.java)

## Context and Idea

gsutil concept: [Parallel Composite Uploads](https://cloud.google.com/storage/docs/gsutil/commands/cp#parallel-composite-uploads)

The google-cloud-storage library for Java this plugin is built with does not have a provided mechanism for parallel 
composite uploads.

In NXRM 3.19, the Amazon S3 blobstore switched to using parallel uploads, see

https://github.com/sonatype/nexus-public/blob/master/plugins/nexus-blobstore-s3/src/main/java/org/sonatype/nexus/blobstore/s3/internal/ParallelUploader.java

This switch has resulted in higher overall throughput for the S3 Blobstores (see https://issues.sonatype.org/browse/NEXUS-19566).
The goal for this feature would be to replicate that parallel upload.

## Implementation

* We don't have content length in the Blobstore API, have an `InputStream` than can be quite large.
* GCS compose method has a hard limit of 32 chunks. Since we don't have the length, we can't
split into 32 equal parts. We can do 31 chunks of a chunkSize parameter, then 1 chunk of the rest.
* We should expose how many times that compose limit as hit with tips on how to re-configure.
* For files smaller than 1 chunk size, we don't pay the cost of shipping the upload request to a thread.
    * The first chunk is written at the expected destination path.
    * If we've read chunkSize, and there is still more data on the stream, schedule the 2nd through final chunks off thread
* A blob write still waits until completion of parallel uploads. This is an important characteristic of all BlobStores; 
Nexus Repository Manager expects consistency and not deferred, eventual consistency.

## Tuning

* Observability via debug logging on `org.sonatype.nexus.blobstore.gcloud.internal.MultipartUploader`
* 5 MB default and 32 chunk compose limit means optimal threads utilization will be in place for files between 10 MB 
and 160 MB in size.
