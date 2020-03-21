package org.sonatype.nexus.blobstore.gcloud.internal;

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
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

/**
 * Integration test intended to deploy the plugin within Nexus Repository manager to confirm that the OSGi
 * packaging is correct, the bundle will activate, and a Google Cloud BlobStore can be created.
 *
 * Depends on GOOGLE_APPLICATION_CREDENTIALS being present in your Environment.
 */
public class GoogleCloudBlobStoreDeploymentIT
  extends GoogleCloudBlobStoreITSupport
{

  private static final String uid = UUID.randomUUID().toString().substring(0,7);
  private static final String bucketName = "deployment-it-" + uid;
  private BlobStoreConfiguration configuration;

  @Inject
  private BlobStoreManager blobStoreManager;

  @Configuration
  public static Option[] configureNexus() {
    return NexusPaxExamSupport.options(
        NexusITSupport.configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-blobstore-google-cloud")
    );
  }

  /**
   *
   *
   * @throws Exception
   */
  @Test
  public void createGoogleCloudBlobStore() throws Exception {
    configuration = blobStoreManager.newConfiguration();
    configuration.setName("GoogleCloudBlobStoreDeploymentIT");
    // TODO GoogleCloudBlobStore.*_KEY is not visible since the class is in an internal package
    configuration.setType("Google Cloud Storage");
    NestedAttributesMap configMap = configuration.attributes("google cloud storage");
    configMap.set("bucket", bucketName);
    configMap.set("location", "us-central1");
    NestedAttributesMap quotaMap = configuration.attributes(BlobStoreQuotaSupport.ROOT_KEY);
    quotaMap.set(BlobStoreQuotaSupport.TYPE_KEY, SpaceUsedQuota.ID);
    quotaMap.set(BlobStoreQuotaSupport.LIMIT_KEY, 512000L);

    blobStoreManager.create(configuration);
  }

  @After
  public void destroyBucket() {
    Storage storage = StorageOptions.newBuilder().build().getService();
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
