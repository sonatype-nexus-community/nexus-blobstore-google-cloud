package org.sonatype.nexus.blobstore.gcloud.internal;

import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;

import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

/**
 * Integration test intended to deploy the plugin within Nexus Repository manager to confirm that the OSGi
 * packaging is correct and the bundle will run.
 */
public class GoogleCloudBlobStoreDeploymentIT
  extends GoogleCloudBlobStoreITSupport
{

  @Configuration
  public static Option[] configureNexus() {
    return NexusPaxExamSupport.options(
        NexusITSupport.configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-blobstore-google-cloud")
    );
  }

  @Test
  public void createGoogleCloudBlobStore() {
    // no-op; this test will fail in inherited rule/before methods if the plugin is not successfully deployed
  }
}
