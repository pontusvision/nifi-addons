#!/bin/bash

DIR="$( cd "$(dirname "$0")" ; pwd -P )"
cd $DIR/docker
docker build --rm . -t pontusvisiongdpr/pontus-nifi-addons-lib

docker push pontusvisiongdpr/pontus-nifi-addons-lib

