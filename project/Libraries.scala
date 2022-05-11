import sbt._

object Libraries {
  val zioVersion            = "1.0.14"
  val zioConfigVersion      = "2.0.4"
  val zioInteropCatsVersion = "3.2.9.1"
  val zioKafka = "0.17.5"
  val zioLogging = "0.5.14"
  val zioCats = "3.1.1.0"
  val circe = "0.14.1"
  val cats = "2.7.0"
  val catsEffect = "3.3.11"
  val guava = "30.1.1-jre"
  val log4j = "2.17.2"
  val disruptor = "3.4.4"
  val jackson = "2.13.2"
  val jacksonDatabind = "2.13.2.2"
  val kafka = "2.8.1"
  val rocksdb: String = "7.1.2"
  val http4s: String = "1.0.0-M27"
  val mongodb: String = "4.5.0"
  val testContainers = "1.17.1"

  val monocle: String = "3.1.0"

  val zioKafkaDependency = "dev.zio" %% "zio-kafka" % zioKafka
  val guavaDependency = "com.google.guava" % "guava" % guava

  val rocksDbDependency = "org.rocksdb" % "rocksdbjni" % rocksdb
  val mongoDbScalaDriver = "org.mongodb.scala" %% "mongo-scala-driver" % mongodb
  val testContainersDependency = "org.testcontainers" % "testcontainers" % testContainers % "test"
  val testContainersMongoDependency = "org.testcontainers" % "mongodb" % testContainers % "test"
  val testContainersKafkaDependency = "org.testcontainers" % "kafka" % testContainers % "test"
  val javaPoetDependency = "com.squareup" % "javapoet" % "1.13.0"
  val autoServiceDependency = "com.google.auto.service" % "auto-service" % "1.0.1"
  val lombokDependency = "org.projectlombok" % "lombok" % "1.18.24"

  val disruptorDependency = "com.lmax" % "disruptor" % disruptor

  val catsDependencies: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-core" % cats,
    "org.typelevel" %% "cats-free" % cats,
    "org.typelevel" %% "cats-kernel"       % cats,
    "org.typelevel" %% "cats-effect"       % catsEffect
  )

  val monocleDependencies: Seq[ModuleID] = Seq(
    "dev.optics"                    %% "monocle-core"                   % monocle,
    "dev.optics"                    %% "monocle-macro"                  % monocle
  )



  val zioDependencies: Seq[ModuleID] = Seq(
    "dev.zio"       %% "zio"               % zioVersion,
    "dev.zio"       %% "zio-streams"       % zioVersion,
    "dev.zio"       %% "zio-interop-cats"  % zioInteropCatsVersion
  )
  val scalaTestDependency = "org.scalatest" %% "scalatest" % "3.2.12" % "test"
  val zioTestDependencies: Seq[ModuleID] = Seq(
    "dev.zio"       %% "zio-test-sbt"      % zioVersion % "test",
    "dev.zio"       %% "zio-test"          % zioVersion % "test",
    scalaTestDependency
  )

  val zioLoggingDependencies: Seq[ModuleID] = Seq(
    "dev.zio" %% "zio-logging" % zioLogging,
    "dev.zio" %% "zio-logging-slf4j" % zioLogging,
    "org.apache.logging.log4j"       % "log4j-core"                     % log4j,
    "org.apache.logging.log4j"       % "log4j-slf4j-impl"               % log4j,
  )

  val zioConfigDependencies: Seq[ModuleID] = Seq(
    "dev.zio"                       %% "zio-config-magnolia"            % zioConfigVersion,
    "dev.zio"                       %% "zio-config-typesafe"            % zioConfigVersion,
  )

  val http4sDependencies: Seq[ModuleID] = Seq(
    "org.http4s"                    %% "http4s-dsl"                     % http4s,
    "org.http4s"                    %% "http4s-blaze-server"            % http4s,
    "org.http4s"                    %% "http4s-blaze-client"            % http4s
  )

  val jacksonDependencies: Seq[ModuleID] = Seq(
    "com.fasterxml.jackson.core"     % "jackson-databind"               % jacksonDatabind,
    "com.fasterxml.jackson.core"     % "jackson-annotations"            % jackson,
    "com.fasterxml.jackson.module"   % "jackson-module-parameter-names" % jackson,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"          % jackson,
    "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"        % jackson,
    "com.fasterxml.jackson.module"  %% "jackson-module-scala"           % jackson
  )

}