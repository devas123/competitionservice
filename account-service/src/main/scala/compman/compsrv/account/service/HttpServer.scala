package compman.compsrv.account.service

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.util.Timeout
import compman.compsrv.account.actors.AccountRepositorySupervisorActor
import compman.compsrv.account.actors.AccountRepositorySupervisorActor._
import compman.compsrv.account.config.AccountServiceConfig
import compman.compsrv.account.model.mapping.DtoMapping
import compservice.model.protobuf.account.{Account, AddAccountRequestPayload, UpdateAccountRequestPayload}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt

object HttpServer {

  implicit val timeout: Timeout = 3.seconds

  def runServer(config: AccountServiceConfig, accountRepoSupervisor: ActorRef[AccountServiceQueryRequest])(implicit
    system: ActorSystem[_],
    ec: ExecutionContext
  ): Future[Http.ServerBinding] = {
    import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem
    lazy val topLevelRoute: Route =
      // provide top-level path structure here but delegate functionality to subroutes for readability
      pathPrefix(s"${config.version}" / "account") {
        concat(
          pathEnd {
            post {
              entity(as[Array[Byte]]) { bytes =>
                val accountRequestPayload = AddAccountRequestPayload.parseFrom(bytes)
                val id                    = UUID.randomUUID().toString
                val account = Account(
                  id,
                  firstName = accountRequestPayload.firstName,
                  lastName = accountRequestPayload.lastName,
                  email = accountRequestPayload.email,
                  birthDate = accountRequestPayload.birthDate
                )
                val operationPerformed = accountRepoSupervisor.ask[AccountServiceQueryResponse](replyTo =>
                  SaveAccountRequest(DtoMapping.toInternalAccount(account), replyTo)
                )
                processWriteResponse(operationPerformed)
              }
            }
          },
          (get & path("""\S+""".r)) { id =>
            val operationPerformed = accountRepoSupervisor
              .ask[AccountServiceQueryResponse](replyTo => GetAccountRequest(id, replyTo))
            onSuccess(operationPerformed) {
              case AccountRepositorySupervisorActor.GetAccountResponse(value) => value match {
                  case Some(account) => complete(StatusCodes.OK, DtoMapping.toDtoAccount(account).toByteArray)
                  case None          => complete(StatusCodes.NotFound)
                }
              case AccountRepositorySupervisorActor.WriteResponseOk =>
                complete(StatusCodes.InternalServerError -> "Wrong response: WriteResponseOk")
              case AccountRepositorySupervisorActor.ErrorResponse(reason) =>
                complete(StatusCodes.InternalServerError -> reason)
            }
          },
          (post & path("update")) {
            entity(as[Array[Byte]]) { bytes =>
              val updateRequestPayload = UpdateAccountRequestPayload.parseFrom(bytes)
              if (updateRequestPayload.account.isEmpty) { complete(StatusCodes.InternalServerError -> "Payload empty") }
              else {
                val operationPerformed = accountRepoSupervisor.ask[AccountServiceQueryResponse](replyTo =>
                  UpdateAccountRequest(DtoMapping.toInternalAccount(updateRequestPayload.account.get), replyTo)
                )
                processWriteResponse(operationPerformed)
              }
            }
          },
          (delete & path("""\S+""".r)) { id =>
            val operationPerformed = accountRepoSupervisor
              .ask[AccountServiceQueryResponse](replyTo => DeleteAccontRequest(id, replyTo))
            processWriteResponse(operationPerformed)
          }
        )
      }
    Http().newServerAt("localhost", 8080).bind(topLevelRoute)
  }

  private def processWriteResponse(operationPerformed: Future[AccountServiceQueryResponse]) = {
    onSuccess(operationPerformed) {
      case AccountRepositorySupervisorActor.GetAccountResponse(_) =>
        complete(StatusCodes.InternalServerError -> "Wrong response: GetAccountResponse")
      case AccountRepositorySupervisorActor.WriteResponseOk       => complete(StatusCodes.OK)
      case AccountRepositorySupervisorActor.ErrorResponse(reason) => complete(StatusCodes.InternalServerError -> reason)
    }
  }
}
