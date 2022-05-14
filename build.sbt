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
  ))
))

Global / onChangedBuildSource := ReloadOnSourceChanges
Test / parallelExecution      := false
Compile / logLevel            := Level.Debug
val zTestFramework = TestFramework("zio.test.sbt.ZTestFramework")

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val competitionServiceAnnotations = module("compservice-annotations", "compservice-annotations")

lazy val competitionServiceAnnotationProcessor =
  module("compservice-annotation-processor", "compservice-annotation-processor")
    .settings(libraryDependencies ++= Seq(guavaDependency, javaPoetDependency, autoServiceDependency))
    .dependsOn(competitionServiceAnnotations)

lazy val competitionServiceModel = module("competition-serv-model", "competition-serv-model")
  .settings(libraryDependencies ++= jacksonDependencies ++ Seq(lombokDependency))
  .enablePlugins(AnnotationProcessorPlugin)
  .dependsOn(competitionServiceAnnotations, competitionServiceAnnotationProcessor)

lazy val competitionServiceModelProtobuf = module("competition-serv-protobuf", "competition-serv-protobuf")
  .settings(
    libraryDependencies ++= scalapbProtobufDepenedency,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )

lazy val actorSystem = module("actor-system", "actor-system").settings(
  libraryDependencies ++= zioLoggingDependencies ++ catsDependencies ++ zioDependencies ++ zioTestDependencies,
  testFrameworks := Seq(zTestFramework)
)

lazy val kafkaCommons = module("kafka-common", "kafka-common").settings(
  libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioLoggingDependencies ++ zioTestDependencies ++
    jacksonDependencies ++ zioConfigDependencies ++
    Seq(zioKafkaDependency, disruptorDependency, testContainersKafkaDependency),
  testFrameworks := Seq(zTestFramework),
).dependsOn(actorSystem, commons)

lazy val commons = module("commons", "command-processor/commons").settings(
  libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioLoggingDependencies ++ zioTestDependencies,
  testFrameworks := Seq(zTestFramework)
).dependsOn(competitionServiceModel)

lazy val competitionservice = project.in(file(".")).settings(publish / skip := true)
  .aggregate(commandProcessor, queryProcessor, gatewayService, kafkaCommons, actorSystem)

lazy val commandProcessor = module("command-processor", "command-processor")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv"))
  .settings(
    libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioTestDependencies ++ zioConfigDependencies ++
      zioLoggingDependencies ++ monocleDependencies ++ jacksonDependencies ++
      Seq(zioKafkaDependency, guavaDependency, rocksDbDependency, disruptorDependency, scalaTestDependency),
    testFrameworks       := Seq(zTestFramework, TestFrameworks.ScalaTest),
    Docker / packageName := "command-processor"
  ).settings(stdSettings("command-processor"))
  .dependsOn(commons, competitionServiceModel, actorSystem, kafkaCommons, competitionServiceModelProtobuf)

lazy val queryProcessor = module("query-processor", "query-processor")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv.logic"))
  .settings(
    libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioTestDependencies ++ zioConfigDependencies ++
      zioLoggingDependencies ++ monocleDependencies ++ http4sDependencies ++ jacksonDependencies ++ Seq(
        zioKafkaDependency,
        mongoDbScalaDriver,
        disruptorDependency,
        testContainersKafkaDependency,
        testContainersDependency,
        testContainersMongoDependency,
        scalaTestDependency
      ),
    dependencyOverrides  := Seq("dev.zio" %% "zio-test" % zioVersion % "test"),
    testFrameworks       := Seq(zTestFramework, TestFrameworks.ScalaTest),
    Docker / packageName := "query-processor"
  ).settings(stdSettings("query-processor", Seq.empty))
  .dependsOn(commons, competitionServiceModel, actorSystem, kafkaCommons)

lazy val gatewayService = module("gateway-service", "gateway-service")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv.gateway"))
  .settings(
    libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioTestDependencies ++ zioConfigDependencies ++
      zioLoggingDependencies ++ http4sDependencies ++ jacksonDependencies ++
      Seq(zioKafkaDependency, scalaTestDependency),
    testFrameworks       := Seq(zTestFramework),
    Docker / packageName := "gateway-service"
  ).settings(stdSettings("gateway-service")).dependsOn(commons, competitionServiceModel, actorSystem, kafkaCommons)
