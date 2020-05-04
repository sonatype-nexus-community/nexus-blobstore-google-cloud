package org.sonatype.nexus.blobstore.gcloud.internal;

import java.io.File;
import java.util.Iterator;
import java.util.UUID;

import javax.inject.Inject;

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport;
import org.sonatype.nexus.blobstore.quota.internal.SpaceUsedQuota;
import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;

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
 * Integration test intended to deploy the plugin within Nexus Repository manager to confirm that the OSGi
 * packaging is correct, the bundle will activate, and a Google Cloud BlobStore can be created.
 *
 * Depends on GOOGLE_APPLICATION_CREDENTIALS being present in your Environment (see README at root of project).
 */
public class SuccessfulDeploymentIT
  extends GoogleCloudBlobStoreITSupport
{
  private static final String uid = UUID.randomUUID().toString().substring(0, 7);

  private static final String bucketName = "deployment-it-" + uid;

  @Configuration
  public static Option[] configureNexus() {
    return NexusPaxExamSupport.options(
        NexusITSupport.configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-blobstore-google-cloud")
    );
  }

  @Before
  public void googleApplicationCredentialsPresentInEnvironment() {
    String path = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
    assumeThat(path, not(isEmptyOrNullString()));
    assumeThat(new File(path).exists(), is(true));
  }

  @Test
  public void createGoogleCloudBlobStore() throws Exception {
    BlobStoreConfiguration configuration = newConfiguration("SuccessfulDeploymentIT", bucketName, null);

    blobStoreManager.create(configuration);
  }

  @After
  public void destroyBucket() {
    Storage storage = StorageOptions.newBuilder().build().getService();
    log.debug("Deleting files from {}", bucketName);
    // must delete all the files within the bucket before we can delete the bucket
    Iterator<Blob> list = storage.list(bucketName,
        Storage.BlobListOption.prefix("")).iterateAll()
        .iterator();
    list.forEachRemaining(blob -> blob.delete());

    storage.delete(bucketName);

    log.info("{} bucket deleted", bucketName);
  }
}
