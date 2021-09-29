#!/bin/sh

docker-compose -f docker-compose-backend.yml down
#docker-compose -f docker-compose-front-end.yml down
docker system prune -f