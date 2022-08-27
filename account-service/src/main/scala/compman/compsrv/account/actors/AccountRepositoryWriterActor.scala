package compman.compsrv.account.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.unsafe.IORuntime
import cats.effect.IO
import compman.compsrv.account.actors.AccountRepositorySupervisorActor.{
  AccountServiceQueryResponse,
  ErrorResponse,
  WriteResponseOk
}
import compman.compsrv.account.model.InternalAccount
import compman.compsrv.account.service.AccountRepository

import scala.util.Try

object AccountRepositoryWriterActor {

  sealed trait AccountRepositoryWriterActorApi
  final case class AddAccount(account: InternalAccount, replyTo: ActorRef[AccountServiceQueryResponse])
      extends AccountRepositoryWriterActorApi
  final case class DeleteAccount(id: String, replyTo: ActorRef[AccountServiceQueryResponse])
      extends AccountRepositoryWriterActorApi
  final case class UpdateAccount(account: InternalAccount, replyTo: ActorRef[AccountServiceQueryResponse])
      extends AccountRepositoryWriterActorApi

  def behavior(accountService: AccountRepository): Behavior[AccountRepositoryWriterActorApi] = Behaviors.setup { _ =>
    implicit val runtime: IORuntime = IORuntime.global

    def executeAndSendResponse(io: IO[Unit], replyTo: ActorRef[AccountServiceQueryResponse]): Unit = {
      Try { io.unsafeRunSync() }
        .fold(error => replyTo ! ErrorResponse(error.getMessage), _ => replyTo ! WriteResponseOk)
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
