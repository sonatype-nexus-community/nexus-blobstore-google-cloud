package org.sonatype.nexus.blobstore.gcloud.internal;

import java.util.UUID;

import javax.inject.Inject;

import org.sonatype.nexus.blobstore.MockBlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaSupport;
import org.sonatype.nexus.blobstore.quota.internal.SpaceUsedQuota;
import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;

import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * Integration test intended to deploy the plugin within Nexus Repository manager to confirm that the OSGi
 * packaging is correct and the bundle will run.
 */
public class GoogleCloudBlobStoreDeploymentIT
  extends GoogleCloudBlobStoreITSupport
{

  private static final String uid = UUID.randomUUID().toString().substring(0,7);
  private static final String bucketName = "deployment-it-" + uid;

  @Inject
  private BlobStoreManager blobStoreManager;

  @Configuration
  public static Option[] configureNexus() {
    return NexusPaxExamSupport.options(
        NexusITSupport.configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-blobstore-google-cloud")
    );
  }

  @Test
  public void createGoogleCloudBlobStore() throws Exception {
    assertThat(blobStoreManager, notNullValue());

    BlobStoreConfiguration configuration = new MockBlobStoreConfiguration();
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
}
