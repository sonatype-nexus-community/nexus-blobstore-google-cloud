## Migrating from OrientDB to Datastore

This document intends to capture the experience of migrating an OrientDB backed instance of Nexus Repository
to [one that is using a SQL database](https://help.sonatype.com/repomanager3/installation-and-upgrades/configuring-nexus-repository-pro-for-h2-or-postgresql).

## Running the DB Migrator

First, stop the deployment:

> docker-compose -f docker-compose-local.yml down

The sonatype-work directory, which contains the OrientDB database, is a persistent volume that we mount when running the container.
We need a separate database image to mount the same database with the same user/group and run the [Database Migrator for the same version](https://help.sonatype.com/repomanager3/product-information/download/download-archives---sonatype-nexus-repository-database-migrator)

First create a volume and copy the data from the existing volume so we can test the migration:

1. docker volume create --name nexus3-data-h2
2. docker container run --rm -it -v nexus3-data:/from -v nexus3-data-h2:/to alpine ash -c "cd /from ; cp -av . /to"

Then build the [nexus3-db-migrator image](Dockerfile) in this directory and run it against the copy:

1. docker build -t nexus3-db-migrator .
2. docker run -it -v nexus3-data-h2:/data nexus3-db-migrator
3. The previous command will drop you into a shell for the container, run `java -jar /usr/local/bin/nexus-db-migrator-*.jar -y --migration_type=h2`
4. Once the migrator completes, you can modify `nexus.properties` on that instance to enable the SQL database support instead of OrientDB:

> touch /data/etc/nexus.properties && echo "nexus.datastore.enabled=true" >> /data/etc/nexus.properties

For OSS versions of Nexus Repository, setting `nexus.datastore.enabled=true` will configure the deployment to use
and embedded instance of H2 that will store the database in a file named `/data/db/nexus.mv.db`. No other SQL
databases are available for OSS deployments, nor are any custom H2 configuration options.

Pro versions of Nexus Repository can [follow these instructions to configure either H2 or PostgreSQL](https://help.sonatype.com/repomanager3/installation-and-upgrades/configuring-nexus-repository-pro-for-h2-or-postgresql).

## Starting the deployment up with the new database

At this point, you have two data volumes:

* The original, containing the state of your deployment when you shutdown the instance.
* A copy of the original, that has since been through an upgrade and is ready to run with H2 instead.

Try running the deployment with the latter volume to see if the Nexus Repository deployment comes up successfully. 

If you inspect nexus.log, if you see the `nexus-datastore-mybatis` bundle being installed you are running on H2:

```
2023-04-22 11:40:04,140-0500 INFO  [jetty-main-1] *SYSTEM org.sonatype.nexus.bootstrap.osgi.BootstrapListener - Installing: nexus-oss-edition/3.53.0.SNAPSHOT (nexus-datastore-mybatis/3.53.0.SNAPSHOT)
2023-04-22 11:40:06,000-0500 INFO  [jetty-main-1] *SYSTEM org.ehcache.core.osgi.EhcacheActivator - Detected OSGi Environment (core is in bundle: org.ehcache [134]): Using OSGi Based Service Loading
2023-04-22 11:40:06,259-0500 INFO  [jetty-main-1] *SYSTEM org.sonatype.nexus.bootstrap.osgi.BootstrapListener - Installed: nexus-oss-edition/3.53.0.SNAPSHOT (nexus-datastore-mybatis/3.53.0.SNAPSHOT)
```

If `nexus.datastore.enabled` is not properly set, Nexus Repository will default to loading the OrientDB bundle:

```
2023-04-22 11:49:50,469-0500 INFO  [jetty-main-1] *SYSTEM org.sonatype.nexus.bootstrap.osgi.BootstrapListener - Installing: nexus-oss-edition/3.53.0.SNAPSHOT (nexus-orient/3.53.0.SNAPSHOT)
2023-04-22 11:49:52,387-0500 INFO  [jetty-main-1] *SYSTEM org.ehcache.core.osgi.EhcacheActivator - Detected OSGi Environment (core is in bundle: org.ehcache [134]): Using OSGi Based Service Loading
2023-04-22 11:49:52,675-0500 INFO  [jetty-main-1] *SYSTEM org.sonatype.nexus.bootstrap.osgi.BootstrapListener - Installed: nexus-oss-edition/3.53.0.SNAPSHOT (nexus-orient/3.53.0.SNAPSHOT)
```

Observe your system for a period of time after migration. Once you have deemed the migration successful, the prior 
OrientDB databases can be removed from the persistent volume. From a container that has the sonatype-work volume 
mounted at /data: 

```
rm /data/db/component
rm /data/db/config
rm /data/db/OSystem
rm /data/db/security
```
