package org.sonatype.nexus.blobstore.gcloud.internal.attributes;

import java.util.Map;
import java.util.Properties;

import javax.annotation.Nullable;

import org.sonatype.nexus.blobstore.BlobAttributesSupport;
import org.sonatype.nexus.blobstore.api.BlobAttributes;
import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobMetrics;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreException;

import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreException;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Entity.Builder;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Query;
import com.google.cloud.datastore.QueryResults;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.common.annotations.VisibleForTesting;

import static org.sonatype.nexus.blobstore.api.BlobAttributesConstants.DELETED_ATTRIBUTE;
import static org.sonatype.nexus.blobstore.api.BlobAttributesConstants.DELETED_REASON_ATTRIBUTE;

/**
 * {@link BlobAttributesDao} backed by Google Cloud {@link Datastore}.
 */
class DatastoreBlobAttributesDao
  implements BlobAttributesDao
{

  private Datastore gcsDatastore;

  private KeyFactory keyFactory;

  private String attributesKeyKind;

  DatastoreBlobAttributesDao(final Datastore datastore, final BlobStoreConfiguration configuration) {
    this.gcsDatastore = datastore;
    this.attributesKeyKind = "NXRM_BlobAttributes_" + configuration.getName();
    this.keyFactory = gcsDatastore.newKeyFactory().setKind(attributesKeyKind);
  }

  @VisibleForTesting
  String getAttributesKeyKind() {
    return attributesKeyKind;
  }

  @Override
  public BlobAttributes getAttributes(final BlobId blobId) {
    return from(attributesEntity(blobId));
  }

  @Override
  public void deleteAttributes(final BlobId blobId) {
    Entity attributes = attributesEntity(blobId);
    if (attributes != null) {
      gcsDatastore.delete(attributes.getKey());
    }
  }

  @Override
  public void markDeleted(final BlobId blobId, final String reason) {
    BlobAttributes attributes = getAttributes(blobId);
    if (attributes != null) {
      attributes.setDeleted(true);
      attributes.setDeletedReason(reason);

      storeAttributes(blobId, attributes);
    }
  }

  @Override
  public void undelete(final BlobId blobId) {
    BlobAttributes attributes = getAttributes(blobId);
    if (attributes != null) {
      attributes.setDeleted(false);
      attributes.setDeletedReason(null);

      storeAttributes(blobId, attributes);
    }
  }

  @Override
  public BlobAttributes storeAttributes(final BlobId blobId, final BlobAttributes blobAttributes) {
    Key key = keyFactory.newKey(blobId.asUniqueString());
    Builder builder = Entity.newBuilder(key);

    Properties props = blobAttributes.getProperties();
    if (blobAttributes.isDeleted()) {
      props.put(DELETED_ATTRIBUTE, Boolean.TRUE.toString());
      props.put(DELETED_REASON_ATTRIBUTE, blobAttributes.getDeletedReason());
    }

    props.stringPropertyNames()
        .stream().forEach(name -> builder.set(name, props.getProperty(name)));

    try {
      Entity result = gcsDatastore.put(builder.build());
      return from(result);
    } catch (DatastoreException e) {
      throw new BlobStoreException(e, blobId);
    }
  }

  @Override
  public BlobAttributes storeAttributes(final BlobId blobId, final Map<String, String> headers,
                                        final BlobMetrics metrics) {
    return storeAttributes(blobId, new GCBlobAttributes(headers, metrics));
  }

  Entity attributesEntity(final BlobId blobId) {
    Query<Entity> query = Query.newEntityQueryBuilder()
        .setKind(this.attributesKeyKind)
        .setFilter(PropertyFilter.eq("__key__", keyFactory.newKey(blobId.asUniqueString())))
        .build();
    try {
      QueryResults<Entity> results = gcsDatastore.run(query);
      if (results.hasNext()) {
        return results.next();
      }
      return null;
    } catch (DatastoreException e) {
      throw new BlobStoreException(e, blobId);
    }
  }

  @Nullable
  GCBlobAttributes from(Entity entity) {
    if (entity == null) {
      return null;
    }
    Properties properties = new Properties();
    entity.getNames().forEach(n -> properties.setProperty(n, entity.getString(n)));

    GCBlobAttributes result = new GCBlobAttributes(properties);
    result.readFrom(properties);

    return result;
  }

  class GCBlobAttributes extends BlobAttributesSupport<Properties> {

    protected GCBlobAttributes(final Properties propertiesFile)
    {
      super(propertiesFile, null, null);
    }

    protected GCBlobAttributes(Map<String, String> headers, BlobMetrics metrics) {
      super(new Properties(), headers, metrics);
    }

    @Override
    public void store() {
      writeTo(propertiesFile);
      // TODO need a reference to the Dao to write this out?
    }

    @Override
    public void readFrom(final Properties properties) {
      super.readFrom(properties);
    }
  }
}
