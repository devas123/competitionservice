import sbt._

object Libraries {
  val circe            = "0.14.1"
  val cats             = "2.7.0"
  val catsEffect       = "3.3.11"
  val guava            = "30.1.1-jre"
  val log4j            = "2.17.2"
  val disruptor        = "3.4.4"
  val kafka            = "2.8.1"
  val rocksdb: String  = "7.1.2"
  val http4s: String   = "1.0.0-M27"
  val mongodb: String  = "4.5.0"
  val testContainers   = "1.17.1"
  val akka             = "2.6.19"
  val scalaPbVersion   = "0.12.0"
  val akkaKafkaVersion = "3.0.0"
  val jwt              = "9.1.0"
  val pbkdf2 = "0.7.0"

  val monocle: String         = "3.1.0"
  val akkaActorsDependency    = "com.typesafe.akka"    %% "akka-actor-typed" % akka
  val akkaStreamsDependency   = "com.typesafe.akka"    %% "akka-stream"      % akka
  val scalaPbJson4sDependency = "com.thesamet.scalapb" %% "scalapb-json4s"   % scalaPbVersion
  val pbkdf2Depencendy = "io.github.nremond" %% "pbkdf2-scala" % pbkdf2

  val akkaKafka: Seq[ModuleID] =
    Seq("com.typesafe.akka" %% "akka-stream-kafka" % akkaKafkaVersion, akkaStreamsDependency)

  val akkaStreamsTestKit: ModuleID     = "com.typesafe.akka" %% "akka-stream-testkit"       % akka             % Test
  val akkaStreamKafkaTestKit: ModuleID = "com.typesafe.akka" %% "akka-stream-kafka-testkit" % akkaKafkaVersion % Test

  val akkaKafkaTests: Seq[ModuleID] = Seq(akkaStreamKafkaTestKit, akkaStreamsTestKit)

  val guavaDependency              = "com.google.guava"   % "guava"                    % guava
  val akkaTestDependency: ModuleID = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akka % Test

  val akkaDependencies: Seq[ModuleID] = Seq(akkaActorsDependency, akkaTestDependency)
  val jwtCore                         = "com.github.jwt-scala"   %% "jwt-core"           % jwt
  val rocksDbDependency               = "org.rocksdb"             % "rocksdbjni"         % rocksdb
  val mongoDbScalaDriver              = "org.mongodb.scala"      %% "mongo-scala-driver" % mongodb
  val testContainersDependency        = "org.testcontainers"      % "testcontainers"     % testContainers % "test"
  val testContainersMongoDependency   = "org.testcontainers"      % "mongodb"            % testContainers % "test"
  val testContainersKafkaDependency   = "org.testcontainers"      % "kafka"              % testContainers % "test"
  val javaPoetDependency              = "com.squareup"            % "javapoet"           % "1.13.0"
  val autoServiceDependency           = "com.google.auto.service" % "auto-service"       % "1.0.1"
  val lombokDependency                = "org.projectlombok"       % "lombok"             % "1.18.24"
  val protobufUtilsVersion            = "3.21.1"

  val disruptorDependency = "com.lmax" % "disruptor" % disruptor

  val catsDependencies: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-core"   % cats,
    "org.typelevel" %% "cats-free"   % cats,
    "org.typelevel" %% "cats-kernel" % cats,
    "org.typelevel" %% "cats-effect" % catsEffect
  )

  val monocleDependencies: Seq[ModuleID] =
    Seq("dev.optics" %% "monocle-core" % monocle, "dev.optics" %% "monocle-macro" % monocle)

  val scalaTestDependency = "org.scalatest" %% "scalatest" % "3.2.12" % Test

  val log4jDependencies: Seq[ModuleID] =
    Seq("org.apache.logging.log4j" % "log4j-core" % log4j, "org.apache.logging.log4j" % "log4j-slf4j-impl" % log4j)

  val http4sDependencies: Seq[ModuleID] = Seq(
    "org.http4s" %% "http4s-dsl"          % http4s,
    "org.http4s" %% "http4s-blaze-server" % http4s,
    "org.http4s" %% "http4s-blaze-client" % http4s
  )

  val scalapbProtobufDepenedency: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
    scalaPbJson4sDependency
  )

  val protobufUtils: Seq[ModuleID] = Seq("com.google.protobuf" % "protobuf-java-util" % protobufUtilsVersion)

}
