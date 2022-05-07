# Competition service

This is a repository, containing all the backend modules of the competition service

TODO:
+ Migrate account service
+ Add registration service and payment service


Main modules:
+ Query-processor - Serves the query API
+ Command-processor - Receives commands from Kafka and produces the events
+ Gateway-service - Common Gateway for all operations

To build docker images, run

```shell
sbt docker:publishLocal
```

from the project root (where build.sbt is)

after that run 
```shell
./startApplication.sh
```
