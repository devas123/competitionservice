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

Global / onChangedBuildSource  := ReloadOnSourceChanges

Compile / logLevel := Level.Debug


addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt")
addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

lazy val competitionServiceAnnotations = module("compservice-annotations", "compservice-annotations")

lazy val competitionServiceAnnotationProcessor =
  module("compservice-annotation-processor", "compservice-annotation-processor")
    .settings(
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava"%  "27.1-jre",
        "com.squareup" % "javapoet" % "1.13.0",
        "com.google.auto.service" % "auto-service" % "1.0.1",
      ),
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
    )
    .dependsOn(competitionServiceAnnotations)


lazy val competitionServiceModel = module("competition-serv-model", "competition-serv-model")
  .settings(
    libraryDependencies ++= Seq(
      "org.projectlombok"% "lombok" % "1.18.22",
      "com.fasterxml.jackson.core"     % "jackson-databind"               % jackson,
      "com.fasterxml.jackson.core"     % "jackson-annotations"               % jackson,
      "com.fasterxml.jackson.module"   % "jackson-module-parameter-names" % jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"          % jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"        % jackson,
      "com.kjetland" %% "mbknor-jackson-jsonschema"        % "1.0.39",
    ),
    modelClassesPackage := "compman.compsrv.model",
  )
  .enablePlugins(AnnotationProcessorPlugin)
  .dependsOn(competitionServiceAnnotations, competitionServiceAnnotationProcessor)


lazy val commons = module("commons", "command-processor/commons").settings(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core"         % cats,
    "org.typelevel" %% "cats-free"         % cats,
    "org.typelevel" %% "cats-kernel"       % cats,
    "dev.zio"       %% "zio"               % zioVersion,
    "dev.zio"       %% "zio-interop-cats"  % zioInteropCatsVersion,
    "dev.zio"       %% "zio-streams"       % zioVersion,
    "dev.zio"       %% "zio-logging"       % zioLogging,
    "dev.zio"       %% "zio-logging-slf4j" % zioLogging
  ),
  testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
).dependsOn(competitionServiceModel)

lazy val competitionService = project.in(file(".")).settings(publish / skip := true).aggregate(commandProcessor, commons)

lazy val commandProcessor = module("command-processor", "command-processor")
  .enablePlugins(BuildInfoPlugin)
  .settings(buildInfoSettings("compman.compsrv")).settings(
    libraryDependencies ++= Seq(
      "org.typelevel"                 %% "cats-core"                      % Versions.cats,
      "org.typelevel"                 %% "cats-free"                      % Versions.cats,
      "org.typelevel"                 %% "cats-kernel"                    % Versions.cats,
      "dev.zio"                       %% "zio"                            % zioVersion,
      "dev.zio"                       %% "zio-interop-cats"               % zioInteropCatsVersion,
      "dev.zio"                       %% "zio-streams"                    % zioVersion,
      "dev.zio"                       %% "zio-test-sbt"                   % zioVersion     % "test",
      "dev.zio"                       %% "zio-config-typesafe"            % zioConfigVersion,
      "dev.zio"                       %% "zio-logging"                    % Versions.zioLogging,
      "dev.zio"                       %% "zio-logging-slf4j"              % Versions.zioLogging,
      "dev.zio"                       %% "zio-kafka"                      % Versions.zioKafka,
      "dev.optics"                    %% "monocle-core"                   % Versions.monocle,
      "dev.optics"                    %% "monocle-macro"                  % Versions.monocle,
      "com.google.guava"               % "guava"                          % Versions.guava,
      "org.apache.logging.log4j"       % "log4j-core"                     % Versions.log4j,
      "org.apache.logging.log4j"       % "log4j-slf4j-impl"               % Versions.log4j,
      "com.fasterxml.jackson.core"     % "jackson-databind"               % Versions.jackson,
      "com.fasterxml.jackson.module"   % "jackson-module-parameter-names" % Versions.jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8"          % Versions.jackson,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310"        % Versions.jackson,
      "org.rocksdb"                    % "rocksdbjni"                     % Versions.rocksdb,
      "com.fasterxml.jackson.module"  %% "jackson-module-scala"           % Versions.jackson,
      "io.github.embeddedkafka"       %% "embedded-kafka"                 % Versions.kafka % "test",
      "org.scalatest"                 %% "scalatest"                      % "3.2.9"        % "test"
    ),
    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  ).settings(stdSettings("command-processor"))
  .dependsOn(commons)
  .dependsOn(competitionServiceModel)

//lazy val examples = module("zio-actors-examples", "examples")
//  .settings(
//    skip in publish := true,
//    fork := true,
//    libraryDependencies ++= Seq(
//      "dev.zio" %% "zio-test"     % zioVersion % "test",
//      "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
//    ),
//    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
//  )
//  .dependsOn(zioActors, zioActorsPersistence, zioActorsPersistenceJDBC)

//lazy val zioActorsAkkaInterop = module("zio-actors-akka-interop", "akka-interop")
//  .settings(
//    libraryDependencies ++= Seq(
//      "dev.zio"           %% "zio-test"         % zioVersion % "test",
//      "dev.zio"           %% "zio-test-sbt"     % zioVersion % "test",
//      "com.typesafe.akka" %% "akka-actor-typed" % akkaActorTypedVersion
//    ),
//    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
//  )
//  .dependsOn(zioActors)

//lazy val docs = project
//  .in(file("zio-actors-docs"))
//  .settings(
//    skip.in(publish) := true,
//    moduleName := "zio-actors-docs",
//    scalacOptions -= "-Yno-imports",
//    scalacOptions -= "-Xfatal-warnings",
//    libraryDependencies ++= Seq(
//      "dev.zio" %% "zio" % zioVersion
//    ),
//    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(root),
//    target in (ScalaUnidoc, unidoc) := (baseDirectory in LocalRootProject).value / "website" / "static" / "api",
//    cleanFiles += (target in (ScalaUnidoc, unidoc)).value,
//    docusaurusCreateSite := docusaurusCreateSite.dependsOn(unidoc in Compile).value,
//    docusaurusPublishGhpages := docusaurusPublishGhpages.dependsOn(unidoc in Compile).value
//  )
//  .dependsOn(zioActors, zioActorsPersistence, zioActorsAkkaInterop)
//  .enablePlugins(MdocPlugin, DocusaurusPlugin, ScalaUnidocPlugin)