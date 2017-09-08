package org.sonatype.nexus.blobstore.gcloud.internal;

import java.util.Arrays;
import java.util.List;

import javax.inject.Named;

import org.sonatype.goodies.i18n.I18N;
import org.sonatype.goodies.i18n.MessageBundle;
import org.sonatype.nexus.blobstore.BlobStoreDescriptor;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.StringTextFormField;

@Named(GoogleCloudBlobStoreDescriptor.TYPE)
public class GoogleCloudBlobStoreDescriptor
    implements BlobStoreDescriptor
{
  public static final String TYPE = "Google Cloud Storage";

  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("Google Cloud Storage")
    String name();

    @DefaultMessage("Bucket")
    String bucketName();

    @DefaultMessage("Google Cloud Bucket Name")
    String bucketHelp();
  }

  private final FormField bucket;

  private static final Messages messages = I18N.create(Messages.class);

  public GoogleCloudBlobStoreDescriptor() {
    bucket = new StringTextFormField(
        GoogleCloudBlobStore.BUCKET_KEY,
        messages.bucketName(),
        messages.bucketHelp(),
        FormField.MANDATORY
    );
  }

  @Override
  public String getName() {
    return messages.name();
  }

  @Override
  public List<FormField> getFormFields() {
    return Arrays.asList(bucket);
  }
}
