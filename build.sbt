import BuildHelper._
import CommonProjects._
import Libraries._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker

inThisBuild(List(
  organization := "compman.compsrv",
  homepage     := Some(url("https://github.com/devas123/competitionservice")),
  licenses     := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  developers :=
    List(Developer("devas123", "Grigorii Grigorev", "grigorii.grigorev@gmail.com", url("https://github.com/devas123"))),
  scmInfo := Some(ScmInfo(
    url("https://github.com/devas123/competitionservice"),
    "scm:git:git@github.com:devas123/competitionservice.git"
  )),
  Test / parallelExecution      := false,
  Global / onChangedBuildSource := ReloadOnSourceChanges
))

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val competitionServiceModelProtobuf = module("competition-serv-protobuf", "competition-serv-protobuf").settings(
  libraryDependencies ++= scalapbProtobufDepenedency,
  Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value / "scalapb")
)

lazy val kafkaCommons = module("kafka-common", "kafka-common").settings(
  libraryDependencies ++= akkaKafka ++ akkaKafkaTests ++ akkaDependencies ++ log4jDependencies,
  libraryDependencies += disruptorDependency,
  libraryDependencies += testContainersDependency,
  libraryDependencies += scalaTestDependency,
  testFrameworks := Seq(TestFrameworks.ScalaTest)
).dependsOn(competitionServiceModelProtobuf)

lazy val commons = module("commons", "command-processor/commons")
  .settings(libraryDependencies ++= catsDependencies ++ akkaDependencies ++ protobufUtils)
  .dependsOn(competitionServiceModelProtobuf)

lazy val competitionservice = project.in(file(".")).settings(publish / skip := true)
  .aggregate(commandProcessor, queryProcessor, gatewayService, kafkaCommons, accountService)

lazy val commandProcessor = module("command-processor", "command-processor")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv"))
  .settings(
    libraryDependencies ++= catsDependencies ++ monocleDependencies ++ akkaDependencies ++ akkaKafkaTests ++
      Seq(guavaDependency, rocksDbDependency, disruptorDependency, scalaTestDependency),
    testFrameworks       := Seq(TestFrameworks.ScalaTest),
    Docker / packageName := "command-processor"
  ).settings(stdSettings("command-processor")).dependsOn(commons, kafkaCommons)

lazy val queryProcessor = module("query-processor", "query-processor")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv.logic"))
  .settings(
    libraryDependencies ++= catsDependencies ++ akkaKafkaTests ++ monocleDependencies ++ http4sDependencies ++ Seq(
      mongoDbScalaDriver,
      disruptorDependency,
      testContainersDependency,
      testContainersMongoDependency,
      scalaTestDependency,
      scalaPbJson4sDependency
    ) ++ akkaDependencies,
    testFrameworks       := Seq(TestFrameworks.ScalaTest),
    Docker / packageName := "query-processor"
  ).settings(stdSettings("query-processor", Seq.empty)).dependsOn(commons, kafkaCommons)

lazy val accountService = module("account-service", "account-service")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv.logic"))
  .settings(
    libraryDependencies ++= catsDependencies ++ monocleDependencies ++ Seq(
      mongoDbScalaDriver,
      disruptorDependency,
      testContainersDependency,
      testContainersMongoDependency,
      scalaTestDependency,
      akkaActorsDependency,
      akkaStreamsDependency,
      akkaHttpDependency,
      scalaPbJson4sDependency,
      akkaTestDependency
    ),
    testFrameworks       := Seq(TestFrameworks.ScalaTest),
    Docker / packageName := "account-service"
  ).settings(stdSettings("account-service", Seq.empty)).dependsOn(commons, kafkaCommons)

lazy val gatewayService = module("gateway-service", "gateway-service")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv.gateway"))
  .settings(
    libraryDependencies ++= catsDependencies ++ akkaDependencies ++ http4sDependencies ++ Seq(scalaTestDependency),
    testFrameworks       := Seq(TestFrameworks.ScalaTest),
    Docker / packageName := "gateway-service"
  ).settings(stdSettings("gateway-service")).dependsOn(commons, kafkaCommons)
