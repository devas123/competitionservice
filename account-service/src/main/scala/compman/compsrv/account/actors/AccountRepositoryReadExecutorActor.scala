package compman.compsrv.account.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import compman.compsrv.account.model.InternalAccount
import compman.compsrv.account.model.mapping.DtoMapping
import compservice.model.protobuf.account.{AccountServiceResponse, GetAccountResponsePayload}
import compservice.model.protobuf.model.ErrorResponse

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object AccountRepositoryReadExecutorActor {
  sealed trait AccountRepositoryReadExecutorApi
  private object Stop                                         extends AccountRepositoryReadExecutorApi
  private case class Response(value: Option[InternalAccount]) extends AccountRepositoryReadExecutorApi
  private case class OperationFailed(value: Throwable)        extends AccountRepositoryReadExecutorApi
  def behavior(
    io: IO[Option[InternalAccount]],
    timeout: FiniteDuration,
    replyTo: ActorRef[AccountServiceResponse]
  ): Behavior[AccountRepositoryReadExecutorApi] = Behaviors.setup { ctx =>
    implicit val runtime: IORuntime = IORuntime.global
    Behaviors.withTimers { timers =>
      timers.startSingleTimer("Stop", Stop, timeout)
      ctx.pipeToSelf(io.unsafeToFuture()) {
        case Failure(e)     => OperationFailed(e)
        case Success(value) => Response(value)
      }
      Behaviors.receiveMessage {
        case OperationFailed(value) =>
          ctx.log.error("Error during IO operation.", value)
          replyTo ! AccountServiceResponse().withErrorResponse(ErrorResponse(Some(value.getMessage)))
          Behaviors.stopped
        case Stop => Behaviors.stopped
        case Response(value) =>
          replyTo ! AccountServiceResponse()
            .withGetAccountResponsePayload(GetAccountResponsePayload(value.map(DtoMapping.toDtoAccount)))
          Behaviors.stopped
      }
    }
  }
}
