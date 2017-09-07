package org.sonatype.nexus.blobstore.gcloud.internal;

import javax.inject.Named;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@Named
public class GoogleCloudStorageFactory
{

  Storage create() {
    Storage storage = StorageOptions.getDefaultInstance().getService();
    return storage;
  }
}
