package compman.compsrv.account.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import compman.compsrv.account.actors.AccountRepositoryWriterActor.{AccountRepositoryWriterActorApi, AddAccount, DeleteAccount, UpdateAccount}
import compman.compsrv.account.actors.AccountServiceAuthenticateActor.{AccountServiceAuthenticateActorApi, Authenticate}
import compman.compsrv.account.config.AccountServiceConfig
import compman.compsrv.account.model.InternalAccount
import compman.compsrv.account.service.AccountRepository
import compservice.model.protobuf.account.AccountServiceResponse

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

object AccountRepositorySupervisorActor {

  sealed trait AccountServiceQueryRequest
  final case class GetAccountRequest(id: String, replyTo: ActorRef[AccountServiceResponse])
      extends AccountServiceQueryRequest
  final case class SaveAccountRequest(account: InternalAccount, replyTo: ActorRef[AccountServiceResponse])
      extends AccountServiceQueryRequest
  final case class UpdateAccountRequest(account: InternalAccount, replyTo: ActorRef[AccountServiceResponse])
      extends AccountServiceQueryRequest
  final case class DeleteAccontRequest(accountId: String, replyTo: ActorRef[AccountServiceResponse])
      extends AccountServiceQueryRequest
  final case class AuthenticateAccountRequest(username: String, password: String, replyTo: ActorRef[AccountServiceResponse])
      extends AccountServiceQueryRequest
  private def initialized(
    accountWriter: ActorRef[AccountRepositoryWriterActorApi],
    accountAuthenticator: ActorRef[AccountServiceAuthenticateActorApi],
    accountService: AccountRepository,
    requestTimeout: FiniteDuration
  ): Behavior[AccountServiceQueryRequest] = Behaviors.setup { ctx =>
    Behaviors.receiveMessage {
      case AuthenticateAccountRequest(username, password, replyTo) =>
        accountAuthenticator ! Authenticate(username, password)(replyTo)
        Behaviors.same
      case GetAccountRequest(id, replyTo) =>
        ctx.spawn(
          AccountRepositoryReadExecutorActor.behavior(accountService.getAccountById(id), requestTimeout, replyTo),
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
    config: AccountServiceConfig
  ): Behavior[AccountServiceQueryRequest] = Behaviors.setup { ctx =>
    // We will have one (or a pool in future) writer
    // and we will create short-lived readers for each read request.
    val accountWriter = ctx.spawn(AccountRepositoryWriterActor.behavior(accountService, config.addMockUser), "accountWriter")
    val accountAuthenticator = ctx.spawn(AccountServiceAuthenticateActor.behavior(accountService, config.authentication), "accountAuthenticator")
    initialized(accountWriter, accountAuthenticator, accountService, config.requestTimeout)
  }
}
