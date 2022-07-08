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

val zTestFramework = TestFramework("zio.test.sbt.ZTestFramework")

addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val competitionServiceModelProtobuf = module("competition-serv-protobuf", "competition-serv-protobuf").settings(
  libraryDependencies ++= scalapbProtobufDepenedency,
  Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value / "scalapb")
)

lazy val kafkaCommons = module("kafka-common", "kafka-common").settings(
  libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioLoggingDependencies ++ zioTestDependencies ++
    Seq(zioKafkaDependency, disruptorDependency, testContainersKafkaDependency, akkaDependency),
  testFrameworks := Seq(zTestFramework)
).dependsOn(commons)

lazy val commons = module("commons", "command-processor/commons").settings(
  libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioLoggingDependencies ++ zioTestDependencies ++
    protobufUtils,
  testFrameworks := Seq(zTestFramework)
).dependsOn(competitionServiceModelProtobuf)

lazy val competitionservice = project.in(file(".")).settings(publish / skip := true)
  .aggregate(commandProcessor, queryProcessor, gatewayService, kafkaCommons)

lazy val commandProcessor = module("command-processor", "command-processor")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv"))
  .settings(
    libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioTestDependencies ++ zioConfigDependencies ++
      zioLoggingDependencies ++ monocleDependencies ++ Seq(
        zioKafkaDependency,
        guavaDependency,
        rocksDbDependency,
        disruptorDependency,
        scalaTestDependency,
        akkaDependency
      ),
    testFrameworks       := Seq(zTestFramework, TestFrameworks.ScalaTest),
    Docker / packageName := "command-processor"
  ).settings(stdSettings("command-processor")).dependsOn(commons, kafkaCommons)

lazy val queryProcessor = module("query-processor", "query-processor")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv.logic"))
  .settings(
    libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioTestDependencies ++ zioConfigDependencies ++
      zioLoggingDependencies ++ monocleDependencies ++ http4sDependencies ++ Seq(
        zioKafkaDependency,
        mongoDbScalaDriver,
        disruptorDependency,
        testContainersKafkaDependency,
        testContainersDependency,
        testContainersMongoDependency,
        scalaTestDependency,
        akkaDependency,
        "com.thesamet.scalapb" %% "scalapb-json4s" % "0.12.0"
      ),
    dependencyOverrides  := Seq("dev.zio" %% "zio-test" % zioVersion % "test"),
    testFrameworks       := Seq(zTestFramework, TestFrameworks.ScalaTest),
    Docker / packageName := "query-processor"
  ).settings(stdSettings("query-processor", Seq.empty)).dependsOn(commons, kafkaCommons)

lazy val gatewayService = module("gateway-service", "gateway-service")
  .enablePlugins(BuildInfoPlugin, DockerPlugin, JavaAppPackaging).settings(buildInfoSettings("compman.compsrv.gateway"))
  .settings(
    libraryDependencies ++= catsDependencies ++ zioDependencies ++ zioTestDependencies ++ zioConfigDependencies ++
      zioLoggingDependencies ++ http4sDependencies ++ Seq(zioKafkaDependency, scalaTestDependency, akkaDependency),
    testFrameworks       := Seq(zTestFramework),
    Docker / packageName := "gateway-service"
  ).settings(stdSettings("gateway-service")).dependsOn(commons, kafkaCommons)
