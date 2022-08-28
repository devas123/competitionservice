package compman.compsrv.account.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.unsafe.IORuntime
import cats.effect.IO
import compman.compsrv.account.model.InternalAccount
import compman.compsrv.account.service.AccountRepository
import compservice.model.protobuf.account.AccountServiceResponse
import compservice.model.protobuf.model.ErrorResponse

import scala.util.Try

object AccountRepositoryWriterActor {

  sealed trait AccountRepositoryWriterActorApi
  final case class AddAccount(account: InternalAccount, replyTo: ActorRef[AccountServiceResponse])
      extends AccountRepositoryWriterActorApi
  final case class DeleteAccount(id: String, replyTo: ActorRef[AccountServiceResponse])
      extends AccountRepositoryWriterActorApi
  final case class UpdateAccount(account: InternalAccount, replyTo: ActorRef[AccountServiceResponse])
      extends AccountRepositoryWriterActorApi

  def behavior(accountService: AccountRepository): Behavior[AccountRepositoryWriterActorApi] = Behaviors.setup { _ =>
    implicit val runtime: IORuntime = IORuntime.global

    def executeAndSendResponse(io: IO[Unit], replyTo: ActorRef[AccountServiceResponse]): Unit = {
      Try { io.unsafeRunSync() }.fold(
        error => replyTo ! AccountServiceResponse().withErrorResponse(ErrorResponse(Some(error.getMessage))),
        _ => replyTo ! AccountServiceResponse.defaultInstance
      )
    }
    Behaviors.receiveMessage {
      case AddAccount(account, replyTo) =>
        executeAndSendResponse(accountService.saveAccount(account), replyTo)
        Behaviors.same
      case DeleteAccount(id, replyTo) =>
        executeAndSendResponse(accountService.deleteAccount(id), replyTo)
        Behaviors.same
      case UpdateAccount(account, replyTo) =>
        executeAndSendResponse(accountService.updateAccount(account), replyTo)
        Behaviors.same
    }
  }
}
