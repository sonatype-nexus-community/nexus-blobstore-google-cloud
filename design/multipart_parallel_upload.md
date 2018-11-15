# Multipart Parallel Upload Design

This document is intended to capture the context around the component within this blobstore that provides 
Parallel Multipart Upload:

[MultipartUploader.java](../src/main/java/org/sonatype/nexus/blobstore/gcloud/internal/MultipartUploader.java)

## Context and Idea

gsutil concept: [Parallel Composite Uploads](https://cloud.google.com/storage/docs/gsutil/commands/cp#parallel-composite-uploads)

The google-cloud-storage library for Java this blobstore is built with does not have baked in mechanism for parallel composite uploads.
The storage#create function is synchronous. Fine for some workloads

It does support however, the Compose request in the API. 
This module then implements this client using the Storage request and Compose request functions.

## Implementation

* Don't have content length in the API, have a stream.
* GCS compose method has a hard limit of 32. Since we don't have the length, we can't
split into 32 equal parts. We can do 31 chunks of a chunkSize parameter, then 1 chunk of the rest.
* Exposes the "composeLimitHit" field
* For files smaller than the chunk size, we don't pay the cost of shipping the upload request to a thread.
    * The first chunk is written at the expected destination path.
    * If we've read chunkSize, and there is still more data on the stream, schedule the 2nd through final chunks off thread
* A blob write still waits until completion of parallel uploads. This is an important characteristic of all BlobStores; 
Nexus Repository Manager expects consistency and not deferred, eventual consistency.

## Tuning

* debug logging for `org.sonatype.nexus.blobstore.gcloud.internal.MultipartUploader`
* 5 MB default and 32 chunk compose limit means optimal threads utilization will be in place for files between 10 MB 
and 160 MB in size.
* When to increase? When you have fewer CPUs, and larger files.
* To effectively disable parallel uploads, you could put max int in. Will upload on the request thread
