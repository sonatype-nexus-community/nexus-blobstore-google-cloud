# Using Object Lifecycle for soft-deleted blobs

## Context

At time of writing, this blobstore plugin leverages two different GCP services: Object Storage and Datastore.
Datastore has been used for storing blobstore metadata (links to other ADRs).

In order to simplify deployment and minimize access needed, the goal of this discovery is to eliminate the use of 
Datastore for soft-deleted blobs.

The use of Datastore for blobstore total count and size is out of scope for this discovery effort.

## Comparison 

### Benefits

* By deferring this clean up task to Google's infrastructure, we no longer have to pay the cost of 
maintaining an index for soft-deleted blobs.
* There is no default clean-up task to remove soft-deleted blobs. This step is easily and oft-forgotten, leaving
soft-deleted blobs to build up to a point where running the task may provide challenges (the time and resources
 to complete grow linearly).
* Since Google's infrastructure is deleting the expired assets, we don't have to spend the CPU/RAM to delete these 
blobs, reserving more capacity for other Nexus Repository Manager operations.

### Drawbacks

* Migration of existing blobstores can be a challenge. As shown in the following section, we will have to add holds
retroactively to all objects in the bucket that correspond to live blobs.

### Summary

## How

In the equivalent blobstore plugin for Amazon Web Services S3, soft-deleted blobs are marked with a custom tag, and
a lifecycle policy is attached to the bucket to only delete items *with that tag* that haven't been modified in N days.

Google Cloud Object Storage doesn't have a similar ability to define lifecycle rules based on custom tags. 

The closest alternative we can use is to set a Lifecycle policy based on Age (since last modification) AND use
Event Based Holds on objects that are not to be deleted. An object with a hold on it will not be deleted or modified by
a lifecycle policy. Once the hold is removed, the object can be subject to action by the policy.

## In a new bucket

If we are starting from an empty blobstore:

* Set a default value for event hold to true
* Establish a Lifecycle policy that automatically deletes objects that are N days old.
* Set an event hold on every live blob that is stored. The hold will block the lifecycle policy from deleting objects.
* Modify the soft-delete function to simply remove the event based hold on properties and content files.

The removal of the event based hold updates the modification time of the object in the bucket. The "soft-deleted" object
missing the hold will be deleted N days after removal of the hold.

## Migrating 

Define a new bucket type: 'gcp/v2'.
Prior to this date all buckets were 'gcp/v1'.

If we find during start-up that 'gcp/v1' is present:

* Allow rest of start to proceed, but have some health-check flag to indicate degradation while event-based holds are being applied.
* Leave the bucket marked gcp/v1 for now, and don't create the lifecycle policy
* Write new blobs with event-based holds
* Asynchronously:
  1. Iterate through every single blob properties
  2. Check if the object is soft-deleted. 
  3. If it is, great do nothing
  4. If it is not soft-deleted, set event based holds for both the properties file and the content file
  5. Once all blobs have been visited, set the lifecycle policy on the bucket
  6. Update the bucket type to 'gcp/v2'
  7. Write migration metrics out to metadata.properties
  8. clear the health-check flag

