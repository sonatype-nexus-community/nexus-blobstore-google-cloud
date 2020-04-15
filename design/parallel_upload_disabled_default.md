# Parallel Upload Disabled by Default

This design document intends to describe why the Multipart Parallel Upload introduced
in 0.10 is now disabled by default.

This design document supersedes [multipart_parallel_upload.md](multipart_parallel_upload.md).

## Original benefits observed by parallel upload

In [#53](https://github.com/sonatype-nexus-community/nexus-blobstore-google-cloud/pull/53), there was a notable
change in the Google Cloud Storage SDK usage. [Line 167](https://github.com/sonatype-nexus-community/nexus-blobstore-google-cloud/commit/5ceec29159d17a4b8b99fa4dd41ac54f5713ab2d#diff-1c09814f9bf128b7f3f17c90a3cb37aaR167)
and [line 178](https://github.com/sonatype-nexus-community/nexus-blobstore-google-cloud/commit/5ceec29159d17a4b8b99fa4dd41ac54f5713ab2d#diff-1c09814f9bf128b7f3f17c90a3cb37aaR178) 
in the MultipartUploader marks our switch from uploading the blob from the basic `storage#create(InputStream)` to 
`storage#create(byte[], BlobWriteOption.disableGzipContent())`. The default `storage#create(InputStream)` compressed the 
InputStream, the latter call turns that compression off. That automatic compression turned out to be a significant
bottleneck, as described in the pull request.

The optimization to disable automatic compression only existed for the `storage#create(byte[])` variants however, not
the `storage#create(InputStream)`. This fall back can be seen in [Line 153](https://github.com/sonatype-nexus-community/nexus-blobstore-google-cloud/commit/5ceec29159d17a4b8b99fa4dd41ac54f5713ab2d#diff-1c09814f9bf128b7f3f17c90a3cb37aaR153).

## Realization

While reviewing the switch from `storage#create(InputStream)` to `storage#create(InputStream, BlobTargetOption.disableGzipContent())`, I devised
a test to see - what if we simplified the uploader to just on thread?

The results - nearly identical performance (within margin of error) to parallel.

The true performance improvement from [#53](https://github.com/sonatype-nexus-community/nexus-blobstore-google-cloud/pull/53)
and [#57](https://github.com/sonatype-nexus-community/nexus-blobstore-google-cloud/pull/57) is not from parallelization,
but the switch to `storage#create(InputStream, BlobTargetOption.disableGzipContent())`

## Future direction

Therefore, the parallel upload feature will still be available in the plugin, but it will be disabled by default. 
We can leverage the throughput gains by disabling GZIP compression en route to the bucket, and by disabling the
additional thread pool we can yield some gains in simplicity.

The parallel multipart upload can be enabled by setting `nexus.gcs.multipartupload.chunksize` to a value greater than 0.
This value is in bytes. A small value (a few hundred or thousand bytes) is pretty nonsensical. A recommended starting
point is `2097152` (2 MB). This feature does buffer bytes in memory (on JVM heap), so the higher you go the higher
JVM heap you may need to accommodate highly concurrent NXRM workloads.
