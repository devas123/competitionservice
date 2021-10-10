#!/bin/sh


#startFrontend="${$1:-false}"

docker-compose -f docker-compose-backend.yml --env-file ./.env up -d

#if [ -z "" ]; then
#  docker-compose -f docker-compose-frontend.yml up -d
#fi
