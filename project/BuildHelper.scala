import sbt._
import sbt.Keys._
import sbtbuildinfo._
import BuildInfoKeys._

object BuildHelper {
  private val Scala213        = "2.13.7"

  private val stdOptions = Seq(
    "-encoding",
    "UTF-8",
    "-explaintypes",
    "-Yrangepos",
    "-feature",
    "-language:higherKinds",
    "-language:existentials",
    "-Xlint:_,-type-parameter-shadow",
    "-Xsource:2.13",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings"
  )

  private val stdOpts213 = Seq(
    "-Wunused:imports",
    "-Wvalue-discard",
    "-Wunused:patvars",
    "-Wunused:privates",
    "-Wunused:params",
    "-Wvalue-discard"
  )

  private def extraOptions() = stdOpts213

  def buildInfoSettings(packageName: String) =
    Seq(
      buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
      buildInfoPackage := packageName,
      buildInfoObject := "BuildInfo"
    )

  def stdSettings(prjName: String) =
    Seq(
      name := s"$prjName",
      ThisBuild / scalaVersion := Scala213,
      scalacOptions := stdOptions ++ extraOptions(),
      libraryDependencies ++=
        Seq(
          compilerPlugin("org.typelevel" % s"kind-projector_$Scala213" % "0.13.2")
        ),
      incOptions ~= (_.withLogRecompileOnMacro(false))
    )
}
