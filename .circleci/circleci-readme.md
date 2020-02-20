<!--

    Sonatype Nexus (TM) Open Source Version
    Copyright (c) 2020-present Sonatype, Inc.
    All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.

    Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
    of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
    Eclipse Foundation. All other trademarks are the property of their respective owners.

-->

CI Debug Notes
================
To validate some circleci stuff, I was able to run a “build locally” using the steps below.
The local build runs in a docker container.

  * (Once) Install circleci client (`brew install circleci`)

  * Convert the “real” config.yml into a self contained (non-workspace) config via:

        circleci config process .circleci/config.yml > .circleci/local-config.yml

  * Run a local build with the following command:
          
        circleci local execute -c .circleci/local-config.yml --job 'github-maven-deploy/build-and-test'

    Typically both commands are run together:
    
        circleci config process .circleci/config.yml > .circleci/local-config.yml && circleci local execute -c .circleci/local-config.yml --job 'github-maven-deploy/build-and-test'
    
    With the above command, operations that cannot occur during a local build will show an error like this:
     
      ```
      ... Error: FAILED with error not supported
      ```
    
      However, the build will proceed and can complete “successfully”, which allows you to verify scripts in your config, etc.
      
      If the build does complete successfully, you should see a happy yellow `Success!` message.

Miscellaneous
-------------

To allow your CI build to push changes back to github (e.g. release tags, etc), you need to create setup
 a github "Deploy Key" with write access. The command below will create such a key. Use an empty password.
 See: https://circleci.com/docs/2.0/add-ssh-key/#steps

    ssh-keygen -m PEM -t rsa -b 4096 -C "community-group@sonatype.com" -f <project-name>_github_rsa.key
    
Paste the public key into a new "write" enabled GitHub deploy key with Title: CircleCI Write <project name>

Be sure you check the "Allow write access" option.

    cat <project-name>_github_rsa.key.pub | pbcopy
    
In the CircleCI Web UI, under Permissions -> SSH Permissions -> Add SSH Key, enter "Hostname": github.com

Paste the private key.

    cat <project-name>_github_rsa.key | pbcopy        

As a sanity check, the private key should end with `-----END RSA PRIVATE KEY-----`.

Also update the `ssh-fingerprints:` tag in your config.yml to append the fingerprint of the write key.
