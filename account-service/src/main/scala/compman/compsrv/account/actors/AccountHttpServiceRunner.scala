package compman.compsrv.account.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import cats.effect.{ExitCode, IO}
import cats.effect.unsafe.IORuntime

import scala.util.{Failure, Success}

object AccountHttpServiceRunner {
  sealed trait AccountHttpServiceRunnerApi
  private case class Stop(exitCode: ExitCode, message: Option[String] = None) extends AccountHttpServiceRunnerApi

  def behavior(srv: IO[ExitCode]): Behavior[AccountHttpServiceRunnerApi] = Behaviors.setup { context =>
    implicit val runtime: IORuntime = IORuntime.global
    context.pipeToSelf(srv.unsafeToFuture()) {
      case Failure(exception) => Stop(ExitCode.Error, Some(exception.toString))
      case Success(exitCode)  => Stop(exitCode)
    }
    Behaviors.receiveMessage { case Stop(exitCode, message) =>
      Behaviors
        .stopped(() => context.log.info(s"Query HTTP Server stopped with exit code $exitCode and message $message"))
    }

  }
}
