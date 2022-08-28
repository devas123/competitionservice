package compman.compsrv.query.actors.behavior

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.ExitCode
import compman.compsrv.query.service.QueryHttpApiService.ServiceIO

import scala.util.{Failure, Success}

object QueryHttpServiceRunner extends WithIORuntime {
  sealed trait QueryHttpServiceRunnerApi
  private case class Stop(exitCode: ExitCode, message: Option[String] = None) extends QueryHttpServiceRunnerApi
  def behavior(srv: ServiceIO[Unit]): Behavior[QueryHttpServiceRunnerApi] = Behaviors.setup { context =>
    context.pipeToSelf(srv.unsafeToFuture()) {
      case Failure(exception) => Stop(ExitCode.Error, Some(exception.toString))
      case Success(_)         => Stop(ExitCode.Success)
    }
    Behaviors.receiveMessage {
      case Stop(exitCode, message) =>
        Behaviors.stopped(() => context.log.info(s"Query HTTP Server stopped with exit code $exitCode and message $message"))
    }
  }
}
