package compman.compsrv.account.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.account.actors.AccountRepositoryWriterActor.{AccountRepositoryWriterActorApi, AddAccount, DeleteAccount, UpdateAccount}
import compman.compsrv.account.model.InternalAccount
import compman.compsrv.account.service.AccountRepository

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

object AccountRepositorySupervisorActor {

  sealed trait AccountServiceQueryRequest
  final case class GetAccountRequest(id: String, replyTo: ActorRef[AccountServiceQueryResponse])
      extends AccountServiceQueryRequest
  final case class SaveAccountRequest(account: InternalAccount, replyTo: ActorRef[AccountServiceQueryResponse])
      extends AccountServiceQueryRequest
  final case class UpdateAccountRequest(account: InternalAccount, replyTo: ActorRef[AccountServiceQueryResponse])
      extends AccountServiceQueryRequest
  final case class DeleteAccontRequest(accountId: String, replyTo: ActorRef[AccountServiceQueryResponse])
      extends AccountServiceQueryRequest
  sealed trait AccountServiceQueryResponse
  final case class GetAccountResponse(option: Option[InternalAccount]) extends AccountServiceQueryResponse
  final object WriteResponseOk                                         extends AccountServiceQueryResponse
  final case class ErrorResponse(reason: String)                       extends AccountServiceQueryResponse

  private def initialized(
    accountWriter: ActorRef[AccountRepositoryWriterActorApi],
    accountService: AccountRepository,
    requestTimeout: FiniteDuration
  ): Behavior[AccountServiceQueryRequest] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage {
      case GetAccountRequest(id, replyTo) =>
        ctx.spawn(
          AccountRepositoryReadExecutorActor.behavior(accountService.getAccount(id), requestTimeout, replyTo),
          UUID.randomUUID().toString
        )
        Behaviors.same
      case UpdateAccountRequest(account, replyTo) =>
        accountWriter ! UpdateAccount(account, replyTo)
        Behaviors.same
      case SaveAccountRequest(account, replyTo) =>
        accountWriter ! AddAccount(account, replyTo)
        Behaviors.same
      case DeleteAccontRequest(accountId, replyTo) =>
        accountWriter ! DeleteAccount(accountId, replyTo)
        Behaviors.same
    }
  }
  def behavior(
    accountService: AccountRepository,
    requestTimeout: FiniteDuration
  ): Behavior[AccountServiceQueryRequest] = Behaviors.setup { ctx =>
    // We will have one (or a pool in future) writer
    // and we will create short-lived readers for each read request.
    val accountWriter = ctx.spawn(AccountRepositoryWriterActor.behavior(accountService), "accountWriter")
    initialized(accountWriter, accountService, requestTimeout)
  }
}
