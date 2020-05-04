package org.sonatype.nexus.blobstore.gcloud;

import org.sonatype.nexus.blobstore.api.BlobStoreException;

/**
 * Exception thrown when the Google Cloud Project is mis-configured for use with this plugin.
 *
 * A common example: the Google Cloud Project containing this instance is using Firestore in Native mode, the plugin
 * expects Firestore in Datastore mode.
 */
public class GoogleCloudProjectException
    extends BlobStoreException
{
  public GoogleCloudProjectException(final String message, final Throwable cause)
  {
    super(message, cause, null);
  }
}
