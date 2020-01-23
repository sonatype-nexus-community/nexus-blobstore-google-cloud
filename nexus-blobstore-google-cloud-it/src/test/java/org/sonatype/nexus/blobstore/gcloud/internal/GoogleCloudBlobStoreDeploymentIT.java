package org.sonatype.nexus.blobstore.gcloud.internal;

import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;

import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

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
    //assertNotNull(nxrm.getBlobStoreManager());
  }
}
