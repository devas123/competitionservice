import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin
import sbt.TupleSyntax.t8ToTable8
import sbt.internal.inc.{LoggedReporter, PlainVirtualFileConverter}
import xsbti.compile.{ClassFileManager, Compilers, IncToolOptions, SingleOutput}

import java.util
import scala.language.postfixOps
import scala.sys.process._

object AnnotationProcessorPlugin extends AutoPlugin {

  val CompServiceAnnotationProcessor = config("compservice-annotation-processor").hide

  object autoImport {
    val modelClassesPackage = SettingKey[String]("model-classes-package", "Package to scan.")
  }

  private def compileModels(
    classpath: Classpath,
    javaSourceDirectory: File,
    compilers: Compilers,
    generatedSourcesDirectory: File,
    packageToScan: String,
    streams: TaskStreams
  ) = {
    val outputDirectory: File = generatedSourcesDirectory
    outputDirectory.mkdirs()
    streams.log.info("Output directory : " + outputDirectory)
    val javaSources = javaSourceDirectory ** "*.java"
    val cached = FileFunction
      .cached(streams.cacheDirectory / "competitionmodel", FilesInfo.lastModified, FilesInfo.exists) { (in: Set[File]) =>
        {
          try {
            streams.log
              .info("Going to process the following files for annotation scanning : " + in.map(_.getPath).mkString(","))
            streams.log.info("Generated sources directory : " + generatedSourcesDirectory)
            val files = classpath.map(_.data).map(_.getPath).mkString(":")
            streams.log.info("Class path: " + files)
            val processorName = "compman.compsrv.annotationprocessor.GenerateEventsAnnotationProcessor"
            compilers.javaTools().javac().run(
              in.toArray.map(_.toPath).map(PlainVirtualFileConverter.converter.toVirtualFile),
              Array(
                "-cp",
                files,
                "-proc:only",
                "-processor",
                processorName,
                "-XprintRounds",
                "-s",
                outputDirectory.getAbsolutePath
              ),
              new SingleOutput {
                override def getOutputDirectory: File = outputDirectory
              },
              IncToolOptions.create(util.Optional.empty[ClassFileManager](), true),
              new LoggedReporter(300, streams.log("Javac annotations")),
              streams.log
            )
          } catch {
            case _: sbt.compiler.EvalException => streams.log
                .info("Compilation failed to complete, it might be because of cross dependencies")
          }
          (generatedSourcesDirectory ** "*.java").get.toSet
        }
      }

    cached(javaSources.get.toSet)
  }

  def failIfNonZeroExitStatus(command: String, message: => String, log: Logger): Unit = {
    val result = command !

    if (result != 0) {
      log.error(message)
      sys.error("Failed running command: " + command)
    }
  }

  val CompmanagerAnnotationsTemplate
    : (State, Classpath, sbt.File, sbt.File, sbt.File, Compilers, String, TaskStreams) => Seq[File] = (
    _: State,
    dependencyClassPath: Classpath,
    javaSourceDirectory: File,
    _: File,
    generatedDir: File,
    compilers: Compilers,
    packageToScan: String,
    streams: TaskStreams
  ) => {
    compileModels(dependencyClassPath, javaSourceDirectory, compilers, generatedDir, packageToScan, streams)
    val files = (generatedDir ** "*.java").get.map(_.getAbsoluteFile)
    streams.log("AnnotationProcessorPlugin").info(s"Adding files $files to sources")
    files
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq[Def.Setting[_]](
    Compile / sourceGenerators +=
      ((
        state,
        Compile / dependencyClasspath,
        Compile / sourceDirectory,
        Compile / classDirectory,
        Compile / sourceManaged,
        Compile / compilers,
        modelClassesPackage,
        streams
      ) map CompmanagerAnnotationsTemplate).taskValue
  )

  override def projectConfigurations: Seq[Configuration] = Seq(CompServiceAnnotationProcessor)

  override def requires = JvmPlugin
}
