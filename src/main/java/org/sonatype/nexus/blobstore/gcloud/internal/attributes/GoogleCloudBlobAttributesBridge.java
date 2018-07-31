package org.sonatype.nexus.blobstore.gcloud.internal.attributes;

import java.util.Map;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.blobstore.BlobIdLocationResolver;
import org.sonatype.nexus.blobstore.api.BlobAttributes;
import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobMetrics;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.common.stateguard.Guarded;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;

import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.BUCKET_KEY;
import static org.sonatype.nexus.blobstore.gcloud.internal.GoogleCloudBlobStore.CONFIG_KEY;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.STARTED;

/**
 * Bridge implementation of {@link BlobAttributesDao} for determining whether to use Google Cloud Datastore
 * or Google Cloud Storage properties files for {@link BlobAttributes} storage.
 */
public final class GoogleCloudBlobAttributesBridge
    extends ComponentSupport
    implements BlobAttributesDao
{

  private final boolean useDatastore;

  private final PropertiesFileBlobAttributesDao propertiesDao;

  private final DatastoreBlobAttributesDao datastoreDao;


  /**
   *
   * @param blobStoreConfiguration
   * @param blobIdLocationResolver
   * @param useDatastore
   * @param datastore
   * @param bucket
   * @param storage
   */
  public GoogleCloudBlobAttributesBridge(final BlobStoreConfiguration blobStoreConfiguration,
                                         final BlobIdLocationResolver blobIdLocationResolver,
                                         final boolean useDatastore,
                                         final Datastore datastore,
                                         final Bucket bucket,
                                         final Storage storage) {
    this.useDatastore = useDatastore;
    String bucketName = blobStoreConfiguration.attributes(CONFIG_KEY).require(BUCKET_KEY).toString();
    this.propertiesDao = new PropertiesFileBlobAttributesDao(blobIdLocationResolver, bucket, storage,
        blobStoreConfiguration, bucketName);
    this.datastoreDao = new DatastoreBlobAttributesDao(datastore, blobStoreConfiguration);
  }

  @Override
  public BlobAttributes getAttributes(BlobId blobId) {
    if (useDatastore) {
      return datastoreDao.getAttributes(blobId);
    } else {
      return propertiesDao.getAttributes(blobId);
    }
  }

  @Override
  public void deleteAttributes(BlobId blobId) {
    if (useDatastore) {
      datastoreDao.deleteAttributes(blobId);
    } else {
      propertiesDao.deleteAttributes(blobId);
    }
  }

  @Override
  public void markDeleted(BlobId blobId, String reason) {
    if (useDatastore) {
      datastoreDao.markDeleted(blobId, reason);
    } else {
      propertiesDao.markDeleted(blobId, reason);
    }
  }

  @Override
  public void undelete(final BlobId blobId) {
    if (useDatastore) {
      datastoreDao.undelete(blobId);
    } else {
      propertiesDao.undelete(blobId);
    }
  }

  @Override
  public BlobAttributes storeAttributes(BlobId blobId, BlobAttributes blobAttributes) {
    if (useDatastore) {
      return datastoreDao.storeAttributes(blobId, blobAttributes);
    } else {
      return propertiesDao.storeAttributes(blobId, blobAttributes);
    }
  }

  @Override
  public BlobAttributes storeAttributes(final BlobId blobId,
                                        final Map<String, String> headers,
                                        final BlobMetrics metrics)
  {
    if (useDatastore) {
      return datastoreDao.storeAttributes(blobId, headers, metrics);
    } else {
      return propertiesDao.storeAttributes(blobId, headers, metrics);
    }
  }
}
