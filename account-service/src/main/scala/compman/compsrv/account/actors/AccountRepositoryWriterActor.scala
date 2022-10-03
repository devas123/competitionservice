package compman.compsrv.account.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.effect.unsafe.IORuntime
import cats.effect.IO
import compman.compsrv.account.model.InternalAccount
import compman.compsrv.account.service.AccountRepository
import compservice.model.protobuf.account.AccountServiceResponse
import compservice.model.protobuf.model.ErrorResponse
import io.github.nremond.SecureHash

import java.time.Instant
import scala.util.Try

object AccountRepositoryWriterActor {

  sealed trait AccountRepositoryWriterActorApi
  final case class AddAccount(account: InternalAccount, replyTo: ActorRef[AccountServiceResponse])
      extends AccountRepositoryWriterActorApi
  final case class DeleteAccount(id: String, replyTo: ActorRef[AccountServiceResponse])
      extends AccountRepositoryWriterActorApi
  final case class UpdateAccount(account: InternalAccount, replyTo: ActorRef[AccountServiceResponse])
      extends AccountRepositoryWriterActorApi

  def behavior(accountService: AccountRepository, addMockUser: Boolean): Behavior[AccountRepositoryWriterActorApi] =
    Behaviors.setup { ctx =>
      implicit val runtime: IORuntime = IORuntime.global

      def executeAndSendResponse(io: IO[Unit], replyTo: ActorRef[AccountServiceResponse]): Unit = {
        Try { io.unsafeRunSync() }.fold(
          error => replyTo ! AccountServiceResponse().withErrorResponse(ErrorResponse(Some(error.getMessage))),
          _ => replyTo ! AccountServiceResponse.defaultInstance
        )
      }

      if (addMockUser) {
        val account = InternalAccount(
          "-1",
          "valera",
          "protas",
          "valera@protas.ru",
          Some(Instant.now()),
          SecureHash.createHash("123")
        )
        accountService.getAccountById("-1").flatMap { opt =>
          opt.fold(accountService.saveAccount(account))(acc =>
            accountService.deleteAccount(acc.userId) *> accountService.saveAccount(account)
          )
        }.unsafeRunSync()
        ctx.log.info(s"Added mock user: $account")
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
