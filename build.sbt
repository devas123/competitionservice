import BuildHelper._
import CommonProjects._
import Libraries._
import sbt.{Test, Tests}

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
val zTestFramework = TestFramework("zio.test.sbt.ZTestFramework")

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val competitionServiceAnnotations = module("compservice-annotations", "compservice-annotations")

lazy val competitionServiceAnnotationProcessor =
  module("compservice-annotation-processor", "compservice-annotation-processor")
    .settings(libraryDependencies ++= Seq(guavaDependency, javaPoetDependency, autoServiceDependency))
    .dependsOn(competitionServiceAnnotations)

lazy val competitionServiceModel = module("competition-serv-model", "competition-serv-model")
  .settings(libraryDependencies ++= jacksonDependencies ++ Seq(lombokDependency, jsonSchemaGeneratorDependency))
  .enablePlugins(AnnotationProcessorPlugin)
  .dependsOn(competitionServiceAnnotations, competitionServiceAnnotationProcessor)

lazy val actorSystem = module("actor-system", "actor-system").settings(
  libraryDependencies ++= zioLoggingDependencies ++ catsDependencies ++ zioDependencies ++ zioTestDependencies,
  testFrameworks := Seq(zTestFramework)
)

lazy val kafkaCommons = module("kafka-common", "kafka-common").settings(
  libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioLoggingDependencies ++ zioTestDependencies ++
    jacksonDependencies ++ zioConfigDependencies ++
    Seq(zioKafkaDependency, embeddedKafkaDependency, disruptorDependency),
  testFrameworks := Seq(zTestFramework)
).dependsOn(actorSystem, commons)

lazy val commons = module("commons", "command-processor/commons").settings(
  libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioLoggingDependencies ++ zioTestDependencies,
  testFrameworks := Seq(zTestFramework)
).dependsOn(competitionServiceModel)

lazy val competitionService = project.in(file(".")).settings(publish / skip := true)
  .aggregate(commandProcessor, queryProcessor, gatewayService, kafkaCommons, actorSystem)

lazy val commandProcessor = module("command-processor", "command-processor").enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("compman.compsrv")).settings(
    libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioTestDependencies ++ zioConfigDependencies ++
      zioLoggingDependencies ++ monocleDependencies ++ jacksonDependencies ++ Seq(
        zioKafkaDependency,
        guavaDependency,
        rocksDbDependency,
        embeddedKafkaDependency,
        disruptorDependency,
        scalaTestDependency
      ),
    testFrameworks := Seq(zTestFramework, TestFrameworks.ScalaTest)
  ).settings(stdSettings("command-processor")).dependsOn(commons, competitionServiceModel, actorSystem, kafkaCommons)

lazy val queryProcessor = module("query-processor", "query-processor").enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("compman.compsrv.logic")).settings(
    libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioTestDependencies ++ zioConfigDependencies ++
      zioLoggingDependencies ++ monocleDependencies ++ http4sDependencies ++ jacksonDependencies ++ Seq(
        zioKafkaDependency,
        mongoDbScalaDriver,
        embeddedKafkaDependency,
        disruptorDependency,
        embeddedMongodbDependency,
        scalaTestDependency
      ),
    dependencyOverrides := Seq("dev.zio" %% "zio-test" % zioVersion % "test"),
    testFrameworks      := Seq(zTestFramework, TestFrameworks.ScalaTest),
    Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/report/scalatest"),
    Test / testOptions += Tests.Argument(zTestFramework, "-h", "target/report/ziotest")
  ).settings(stdSettings("query-processor", Seq.empty))
  .dependsOn(commons, competitionServiceModel, actorSystem, kafkaCommons)

lazy val gatewayService = module("gateway-service", "gateway-service").enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("compman.compsrv.gateway")).settings(
    libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioTestDependencies ++ zioConfigDependencies ++
      zioLoggingDependencies ++ http4sDependencies ++ jacksonDependencies ++
      Seq(zioKafkaDependency, scalaTestDependency),
    testFrameworks := Seq(zTestFramework)
  ).settings(stdSettings("gateway-service")).dependsOn(commons, competitionServiceModel, actorSystem, kafkaCommons)
