/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2017-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.blobstore.gcloud.internal;

import java.util.Arrays;
import java.util.List;

import javax.inject.Named;

import org.sonatype.goodies.i18n.I18N;
import org.sonatype.goodies.i18n.MessageBundle;
import org.sonatype.nexus.blobstore.BlobStoreDescriptor;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.StringTextFormField;

@Named(GoogleCloudBlobStore.TYPE)
public class GoogleCloudBlobStoreDescriptor
    implements BlobStoreDescriptor
{
  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("Google Cloud Storage")
    String name();

    @DefaultMessage("Bucket")
    String bucketName();

    @DefaultMessage("Google Cloud Bucket Name")
    String bucketHelp();

    @DefaultMessage("Credentials")
    String credentialPath();

    @DefaultMessage("Absolute path to Google Application Credentials JSON file")
    String credentialHelp();
  }

  private final FormField bucket;
  private final FormField credentialFile;

  private static final Messages messages = I18N.create(Messages.class);

  public GoogleCloudBlobStoreDescriptor() {
    bucket = new StringTextFormField(
        GoogleCloudBlobStore.BUCKET_KEY,
        messages.bucketName(),
        messages.bucketHelp(),
        FormField.MANDATORY
    );

    credentialFile = new StringTextFormField(
        GoogleCloudBlobStore.CREDENTIAL_FILE_KEY,
        messages.credentialPath(),
        messages.credentialHelp(),
        FormField.OPTIONAL
    );
  }

  @Override
  public String getName() {
    return messages.name();
  }

  @Override
  public List<FormField> getFormFields() {
    return Arrays.asList(bucket, credentialFile);
  }
}
