import BuildHelper._
import CommonProjects._
import Versions._

inThisBuild(List(
  organization := "compman.compsrv",
  homepage     := Some(url("https://github.com/devas123/competitionservice")),
  licenses     := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  developers :=
    List(Developer("devas123", "Grigorii Grigorev", "grigorii.grigorev@gmail.com", url("https://github.com/devas123"))),
  scmInfo := Some(ScmInfo(
    url("https://github.com/devas123/competitionservice"),
    "scm:git:git@github.com:devas123/competitionservice.git"
  ))
))

Global / onChangedBuildSource := ReloadOnSourceChanges

Compile / logLevel := Level.Debug

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val competitionServiceAnnotations = module("compservice-annotations", "compservice-annotations")

lazy val competitionServiceAnnotationProcessor =
  module("compservice-annotation-processor", "compservice-annotation-processor").settings(
    libraryDependencies ++= Seq(
      "com.google.guava"        % "guava"        % "27.1-jre",
      "com.squareup"            % "javapoet"     % "1.13.0",
      "com.google.auto.service" % "auto-service" % "1.0.1"
    )
  ).dependsOn(competitionServiceAnnotations)

lazy val competitionServiceModel = module("competition-serv-model", "competition-serv-model").settings(
  libraryDependencies ++= Seq(
    "org.projectlombok"              % "lombok"                         % "1.18.22",
    "com.fasterxml.jackson.core"     % "jackson-databind"               % jackson,
    "com.fasterxml.jackson.core"     % "jackson-annotations"            % jackson,
    "com.fasterxml.jackson.module"   % "jackson-module-parameter-names" % jackson,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"          % jackson,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"        % jackson,
    "com.kjetland"                  %% "mbknor-jackson-jsonschema"      % "1.0.39"
  )
).enablePlugins(AnnotationProcessorPlugin)
  .dependsOn(competitionServiceAnnotations, competitionServiceAnnotationProcessor)

lazy val actorSystem = module("actor-system", "actor-system").settings(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core"         % cats,
    "org.typelevel" %% "cats-free"         % cats,
    "org.typelevel" %% "cats-kernel"       % cats,
    "dev.zio"       %% "zio"               % zioVersion,
    "dev.zio"       %% "zio-interop-cats"  % zioInteropCatsVersion,
    "dev.zio"       %% "zio-streams"       % zioVersion,
    "dev.zio"       %% "zio-logging"       % zioLogging,
    "dev.zio"       %% "zio-logging-slf4j" % zioLogging,
    "dev.zio"       %% "zio-test-sbt"      % zioVersion % "test",
    "dev.zio"       %% "zio-test"          % zioVersion % "test"
  ),
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
)

lazy val kafkaCommons = module("kafka-common", "kafka-common").settings(
  libraryDependencies ++= Seq(
    "org.typelevel"                 %% "cats-core"                      % cats,
    "org.typelevel"                 %% "cats-free"                      % cats,
    "org.typelevel"                 %% "cats-kernel"                    % cats,
    "dev.zio"                       %% "zio"                            % zioVersion,
    "dev.zio"                       %% "zio-interop-cats"               % zioInteropCatsVersion,
    "dev.zio"                       %% "zio-streams"                    % zioVersion,
    "dev.zio"                       %% "zio-logging"                    % zioLogging,
    "dev.zio"                       %% "zio-logging-slf4j"              % zioLogging,
    "dev.zio"                       %% "zio-test-sbt"                   % zioVersion % "test",
    "com.fasterxml.jackson.core"     % "jackson-databind"               % jackson,
    "com.fasterxml.jackson.core"     % "jackson-annotations"            % jackson,
    "com.fasterxml.jackson.module"   % "jackson-module-parameter-names" % jackson,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"          % jackson,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"        % jackson,
    "dev.zio"                       %% "zio-config-magnolia"            % zioConfigVersion,
    "dev.zio"                       %% "zio-config-typesafe"            % zioConfigVersion,
    "dev.zio"                       %% "zio-kafka"                      % zioKafka,
    "io.github.embeddedkafka"       %% "embedded-kafka"                 % kafka      % "test"
  ),
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
).dependsOn(actorSystem, commons)

lazy val commons = module("commons", "command-processor/commons").settings(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core"         % cats,
    "org.typelevel" %% "cats-free"         % cats,
    "org.typelevel" %% "cats-kernel"       % cats,
    "dev.zio"       %% "zio"               % zioVersion,
    "dev.zio"       %% "zio-interop-cats"  % zioInteropCatsVersion,
    "dev.zio"       %% "zio-streams"       % zioVersion,
    "dev.zio"       %% "zio-logging"       % zioLogging,
    "dev.zio"       %% "zio-logging-slf4j" % zioLogging,
    "dev.zio"       %% "zio-test-sbt"      % zioVersion % "test"
  ),
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
).dependsOn(competitionServiceModel)

lazy val competitionService = project.in(file(".")).settings(publish / skip := true)
  .aggregate(commandProcessor, queryProcessor, gatewayService, kafkaCommons, actorSystem)

lazy val commandProcessor = module("command-processor", "command-processor").enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("compman.compsrv")).settings(
    libraryDependencies ++= Seq(
      "org.typelevel"                 %% "cats-core"                      % cats,
      "org.typelevel"                 %% "cats-free"                      % cats,
      "org.typelevel"                 %% "cats-kernel"                    % cats,
      "dev.zio"                       %% "zio"                            % zioVersion,
      "dev.zio"                       %% "zio-interop-cats"               % zioInteropCatsVersion,
      "dev.zio"                       %% "zio-streams"                    % zioVersion,
      "dev.zio"                       %% "zio-test-sbt"                   % zioVersion % "test",
      "dev.zio"                       %% "zio-config-magnolia"            % zioConfigVersion,
      "dev.zio"                       %% "zio-config-typesafe"            % zioConfigVersion,
      "dev.zio"                       %% "zio-logging"                    % zioLogging,
      "dev.zio"                       %% "zio-logging-slf4j"              % zioLogging,
      "dev.zio"                       %% "zio-kafka"                      % zioKafka,
      "dev.optics"                    %% "monocle-core"                   % monocle,
      "dev.optics"                    %% "monocle-macro"                  % monocle,
      "com.google.guava"               % "guava"                          % guava,
      "org.apache.logging.log4j"       % "log4j-core"                     % log4j,
      "org.apache.logging.log4j"       % "log4j-slf4j-impl"               % log4j,
      "com.fasterxml.jackson.core"     % "jackson-databind"               % jackson,
      "com.fasterxml.jackson.module"   % "jackson-module-parameter-names" % jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"          % jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"        % jackson,
      "org.rocksdb"                    % "rocksdbjni"                     % rocksdb,
      "com.fasterxml.jackson.module"  %% "jackson-module-scala"           % jackson,
      "io.github.embeddedkafka"       %% "embedded-kafka"                 % kafka      % "test",
      "org.scalatest"                 %% "scalatest"                      % "3.2.9"    % "test"
    ),
    testFrameworks :=
      Seq(new TestFramework("zio.test.sbt.ZTestFramework"), new TestFramework("org.scalatest.tools.ScalaTestFramework"))
  ).settings(stdSettings("command-processor")).dependsOn(commons, competitionServiceModel, actorSystem, kafkaCommons)

lazy val queryProcessor = module("query-processor", "query-processor").enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("compman.compsrv.logic")).settings(
    libraryDependencies ++= Seq(
      "org.typelevel"                 %% "cats-core"                      % cats,
      "org.typelevel"                 %% "cats-free"                      % cats,
      "org.typelevel"                 %% "cats-kernel"                    % cats,
      "dev.zio"                       %% "zio"                            % zioVersion,
      "dev.zio"                       %% "zio-interop-cats"               % zioInteropCatsVersion,
      "dev.zio"                       %% "zio-streams"                    % zioVersion,
      "dev.zio"                       %% "zio-test-sbt"                   % zioVersion      % "test",
      "dev.zio"                       %% "zio-config-magnolia"            % zioConfigVersion,
      "dev.zio"                       %% "zio-config-typesafe"            % zioConfigVersion,
      "dev.zio"                       %% "zio-logging"                    % zioLogging,
      "dev.zio"                       %% "zio-logging-slf4j"              % zioLogging,
      "dev.zio"                       %% "zio-kafka"                      % zioKafka,
      "dev.optics"                    %% "monocle-core"                   % monocle,
      "dev.optics"                    %% "monocle-macro"                  % monocle,
      "org.http4s"                    %% "http4s-dsl"                     % http4s,
      "org.http4s"                    %% "http4s-blaze-server"            % http4s,
      "org.http4s"                    %% "http4s-blaze-client"            % http4s,
      "org.mongodb.scala"             %% "mongo-scala-driver"             % mongodb,
      "com.fasterxml.jackson.core"     % "jackson-databind"               % jackson,
      "com.fasterxml.jackson.module"   % "jackson-module-parameter-names" % jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"          % jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"        % jackson,
      "com.fasterxml.jackson.module"  %% "jackson-module-scala"           % jackson,
      "io.github.embeddedkafka"       %% "embedded-kafka"                 % kafka           % "test",
      "de.flapdoodle.embed"            % "de.flapdoodle.embed.mongo"      % embeddedMongodb % "test",
      "org.scalatest"                 %% "scalatest"                      % "3.2.9"         % "test"
    ),
    dependencyOverrides := Seq("dev.zio" %% "zio-test" % zioVersion % "test"),
    testFrameworks :=
      Seq(new TestFramework("zio.test.sbt.ZTestFramework"), new TestFramework("org.scalatest.tools.ScalaTestFramework"))
  ).settings(stdSettings("query-processor", Seq.empty))
  .dependsOn(commons, competitionServiceModel, actorSystem, kafkaCommons)

lazy val gatewayService = module("gateway-service", "gateway-service").enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("compman.compsrv.logic")).settings(
    libraryDependencies ++= Seq(
      "org.typelevel"                 %% "cats-core"                      % cats,
      "org.typelevel"                 %% "cats-free"                      % cats,
      "org.typelevel"                 %% "cats-kernel"                    % cats,
      "dev.zio"                       %% "zio"                            % zioVersion,
      "dev.zio"                       %% "zio-interop-cats"               % zioInteropCatsVersion,
      "dev.zio"                       %% "zio-streams"                    % zioVersion,
      "dev.zio"                       %% "zio-test-sbt"                   % zioVersion % "test",
      "dev.zio"                       %% "zio-config-magnolia"            % zioConfigVersion,
      "dev.zio"                       %% "zio-config-typesafe"            % zioConfigVersion,
      "dev.zio"                       %% "zio-logging"                    % zioLogging,
      "dev.zio"                       %% "zio-logging-slf4j"              % zioLogging,
      "dev.zio"                       %% "zio-kafka"                      % zioKafka,
      "org.http4s"                    %% "http4s-dsl"                     % http4s,
      "org.http4s"                    %% "http4s-blaze-server"            % http4s,
      "org.http4s"                    %% "http4s-blaze-client"            % http4s,
      "com.fasterxml.jackson.core"     % "jackson-databind"               % jackson,
      "com.fasterxml.jackson.module"   % "jackson-module-parameter-names" % jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"          % jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"        % jackson,
      "com.fasterxml.jackson.module"  %% "jackson-module-scala"           % jackson,
      "org.scalatest"                 %% "scalatest"                      % "3.2.9"    % "test"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  ).settings(stdSettings("gateway-service")).dependsOn(commons, competitionServiceModel, actorSystem, kafkaCommons)
