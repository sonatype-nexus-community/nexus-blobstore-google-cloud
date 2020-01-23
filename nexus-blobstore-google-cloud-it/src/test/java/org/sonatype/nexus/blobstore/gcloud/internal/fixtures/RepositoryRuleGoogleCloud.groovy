package org.sonatype.nexus.blobstore.gcloud.internal.fixtures

import javax.inject.Provider

import org.sonatype.nexus.repository.manager.RepositoryManager
import org.sonatype.nexus.testsuite.testsupport.fixtures.RepositoryRule

class RepositoryRuleGoogleCloud
    extends RepositoryRule
    implements GoogleBlobStoreRecipes
{

  RepositoryRuleGoogleCloud(final Provider<RepositoryManager> repositoryManagerProvider) {
    super(repositoryManagerProvider)
  }
}
