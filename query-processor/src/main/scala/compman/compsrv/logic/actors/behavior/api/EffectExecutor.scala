package compman.compsrv.logic.actors.behavior.api

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import compservice.model.protobuf.query.{ErrorResponse, QueryServiceResponse}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object EffectExecutor {

  sealed trait EffectExecutorCommand
  final case class ExecutionFinished(result: Try[QueryServiceResponse]) extends EffectExecutorCommand
  object Timeout                                                        extends EffectExecutorCommand

  def behavior(effect: IO[QueryServiceResponse], replyTo: ActorRef[QueryServiceResponse], timeout: FiniteDuration)(
    implicit runtime: IORuntime
  ): Behavior[EffectExecutorCommand] = Behaviors.setup { ctx =>
    ctx.pipeToSelf(effect.unsafeToFuture())(ExecutionFinished)
    Behaviors.withTimers { timers =>
      timers.startSingleTimer("Timeout", Timeout, timeout)
      Behaviors.receiveMessage {
        case ExecutionFinished(result) =>
          replyTo !
            (result match {
              case Failure(exception) => QueryServiceResponse().withErrorResponse(
                  ErrorResponse().withErrorMessage(exception.getMessage).withErrorReason("Internal error")
                )
              case Success(value) => value
            })
          Behaviors.stopped(() => ())
        case Timeout =>
          replyTo ! QueryServiceResponse().withErrorResponse(ErrorResponse().withErrorReason("Internal timeout"))
          Behaviors.stopped(() => ())
      }
    }
  }

}
