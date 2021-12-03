import BuildHelper.stdSettings
import sbt._

object CommonProjects {
  def module(moduleName: String, fileName: String): Project = Project(moduleName, file(fileName))
    .settings(stdSettings(moduleName))
}
