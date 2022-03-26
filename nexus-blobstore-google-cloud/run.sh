#!/bin/bash

mvn package
docker build -t nexus3-google-dev .
docker stack deploy -c docker-compose.yml nexus3-google-dev
