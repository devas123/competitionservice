# Competition service

This is a repository, containing all the backend modules of the competition service

TODO:
+ Migrate account service
+ Add registration service and payment service


Main modules:
+ Query-processor 

To build docker images, run

```shell
sbt docker:publishLocal
```

from the project root (where build.sbt is)

after that run 
```shell
./startApplication.sh
```
