package org.sonatype.nexus.blobstore.gcloud.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.gcloud.GoogleCloudProjectException;
import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assume.assumeThat;

/**
 * Integration test intended to deploy the plugin within Nexus Repository manager in a GCP project configured
 * to use Firestore in Native mode. We expect this to fail; we require the project to use Firestore in Datastore mode.
 *
 * Depends on:
 * <ul>
 *   <li>GOOGLE_FIRESTORE_CREDENTIALS being present in your Environment</li>
 *   <li>GOOGLE_FIRESTORE_CREDENTIALS pointing to a service account key file for a project using Firestore in Native mode</li>
 *   <li>The service account has the storage admin account</li>
 * </ul>
 *
 * The last step is needed as the bucket is created first, then the datastore backed entities.
 */
public class DeploymentFailsForFirestoreNativeIT
  extends GoogleCloudBlobStoreITSupport
{

  private static final String uid = UUID.randomUUID().toString().substring(0,7);
  private static final String bucketName = "deployment-it-" + uid;

  private File firestoreNativeConfiguration;

  @Configuration
  public static Option[] configureNexus() {
    return NexusPaxExamSupport.options(
        NexusITSupport.configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-blobstore-google-cloud")
    );
  }

  @Before
  public void firestoreNativeConfigPresent() {
    String path = System.getenv("GOOGLE_FIRESTORE_CREDENTIALS");
    assumeThat(path, not(isEmptyOrNullString()));

    firestoreNativeConfiguration = new File(path);
    assumeThat(firestoreNativeConfiguration.exists(), is(true));
  }

  @Test(expected = GoogleCloudProjectException.class)
  public void failToCreate() throws Exception {
    BlobStoreConfiguration configuration = newConfiguration("DeploymentFailsForFirestoreNativeIT", bucketName,
        firestoreNativeConfiguration);

    // expect specific exception to be thrown
    blobStoreManager.create(configuration);
  }

  @After
  public void destroyBucket() throws IOException {
    Storage storage = StorageOptions.newBuilder()
        .setCredentials(ServiceAccountCredentials.fromStream(new FileInputStream(firestoreNativeConfiguration)))
        .build().getService();
    log.debug("Deleting files from " + bucketName);
    // must delete all the files within the bucket before we can delete the bucket
    Iterator<Blob> list = storage.list(bucketName,
        Storage.BlobListOption.prefix("")).iterateAll()
        .iterator();
    list.forEachRemaining(blob -> blob.delete());

    storage.delete(bucketName);

    log.info(bucketName + "bucket deleted");
  }
}
