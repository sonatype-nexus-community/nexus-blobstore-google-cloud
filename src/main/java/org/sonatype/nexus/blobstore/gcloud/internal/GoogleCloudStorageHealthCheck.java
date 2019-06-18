package org.sonatype.nexus.blobstore.gcloud.internal;

import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.quota.BlobStoreQuotaService;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Preconditions;

import static java.lang.String.format;

@Named
public class GoogleCloudStorageHealthCheck
    extends HealthCheck
{

  private final Provider<BlobStoreManager> blobStoreManagerProvider;

  @Inject
  public GoogleCloudStorageHealthCheck(final Provider<BlobStoreManager> blobStoreManagerProvider)
  {
    this.blobStoreManagerProvider = Preconditions.checkNotNull(blobStoreManagerProvider);
  }

  @Override
  protected Result check() {
    Iterable<BlobStore> blobstoreItr = blobStoreManagerProvider.get().browse();
    long number = StreamSupport.stream(blobstoreItr.spliterator(), false)
        .filter(b -> b instanceof GoogleCloudBlobStore).count();
    return Result.healthy(format("%s Google Cloud Blobstores are healthy", number));
  }
}
