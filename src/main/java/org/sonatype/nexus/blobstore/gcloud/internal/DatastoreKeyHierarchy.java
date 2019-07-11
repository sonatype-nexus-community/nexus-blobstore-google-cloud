package org.sonatype.nexus.blobstore.gcloud.internal;

import com.google.cloud.datastore.PathElement;

/**
 * Class to document the hierarchy of keys for our {@link com.google.cloud.datastore.Datastore} usage.
 */
final class DatastoreKeyHierarchy
{
  static final PathElement NXRM_ROOT = PathElement.of("Sonatype", "Nexus Repository Manager");

  static final String GOOGLE_CLOUD_BLOB_STORE = "Google Cloud BlobStore";
}
