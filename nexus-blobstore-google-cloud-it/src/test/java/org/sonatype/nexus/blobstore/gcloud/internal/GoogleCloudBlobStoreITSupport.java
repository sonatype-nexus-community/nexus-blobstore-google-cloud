package org.sonatype.nexus.blobstore.gcloud.internal;

import org.sonatype.nexus.blobstore.gcloud.internal.fixtures.RepositoryRuleGoogleCloud;
import org.sonatype.nexus.testsuite.testsupport.RepositoryITSupport;

import org.junit.Rule;

public class GoogleCloudBlobStoreITSupport
    extends RepositoryITSupport
{

  @Rule
  public RepositoryRuleGoogleCloud nxrm = new RepositoryRuleGoogleCloud(() -> repositoryManager);

  @Override
  protected RepositoryRuleGoogleCloud createRepositoryRule() {
    return new RepositoryRuleGoogleCloud(() -> repositoryManager);
  }

  public GoogleCloudBlobStoreITSupport() {
  }
}
