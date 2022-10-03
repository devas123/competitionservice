package compman.compsrv.account.service

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import cats.effect.IO
import compman.compsrv.account.actors.AccountRepositorySupervisorActor._
import compman.compsrv.account.model.mapping.DtoMapping
import compman.compsrv.http4s.loggerMiddleware
import compservice.model.protobuf.account._
import org.http4s.{Challenge, HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`WWW-Authenticate`
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.duration.DurationInt

object HttpServer {

  implicit val timeout: Timeout = 3.seconds
  private val dsl               = Http4sDsl[IO]

  private val log = LoggerFactory.getLogger(classOf[HttpServer.type])

  import dsl._

  private def sendApiCommandAndReturnResponse[Command](
    apiActor: ActorRef[Command],
    apiCommandWithCallbackCreator: ActorRef[AccountServiceResponse] => Command
  )(implicit scheduler: Scheduler): IO[Response[IO]] = {
    IO.fromFuture[AccountServiceResponse](IO(apiActor.ask(apiCommandWithCallbackCreator))).attempt.flatMap {
      case Left(ex)     => IO(log.error("Error while processing api request", ex)) *> InternalServerError(ex.getMessage)
      case Right(value) => Ok(value.toByteArray)
    }
  }

  def routes(accountRepoSupervisor: ActorRef[AccountServiceQueryRequest])(implicit
    system: ActorSystem[_]
  ): HttpRoutes[IO] = loggerMiddleware(rawRoutes(accountRepoSupervisor))(log)
  private def rawRoutes(
    accountRepoSupervisor: ActorRef[AccountServiceQueryRequest]
  )(implicit system: ActorSystem[_]): HttpRoutes[IO] = {
    import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem

    HttpRoutes.of[IO] {
      case req @ POST -> Root / "account" / "authenticate" => for {
          body <- req.body.covary[IO].chunkAll.compile.toList
          bytes = body.flatMap(_.toList).toArray
          authenticateRequestPayload = AuthenticateRequestPayload.parseFrom(bytes)
          res <- IO.fromFuture[AccountServiceResponse](IO(accountRepoSupervisor.ask(replyTo =>
            AuthenticateAccountRequest(
              authenticateRequestPayload.username,
              authenticateRequestPayload.password,
              replyTo
            )
          ))).attempt.flatMap {
            case Left(ex) => IO(log.error("Error while processing api request", ex)) *>
                InternalServerError(ex.getMessage)
            case Right(value) =>
              if (value.payload.isAuthenticationResponsePayload) { Ok(value.toByteArray) }
              else {
                Unauthorized.apply(
                  `WWW-Authenticate`(
                    Challenge("Basic", value.payload.errorResponse.flatMap(_.errorMessage).getOrElse(""))
                  ),
                  value.toByteArray
                )
              }
          }
        } yield res
      case req @ POST -> Root / "account" / "register" => for {
          body <- req.body.covary[IO].chunkAll.compile.toList
          bytes                 = body.flatMap(_.toList).toArray
          accountRequestPayload = AddAccountRequestPayload.parseFrom(bytes)
          id                    = UUID.randomUUID().toString
          account = Account(
            id,
            firstName = accountRequestPayload.firstName,
            lastName = accountRequestPayload.lastName,
            email = accountRequestPayload.email,
            birthDate = accountRequestPayload.birthDate,
            password = accountRequestPayload.password
          )
          res <- sendApiCommandAndReturnResponse[AccountServiceQueryRequest](
            accountRepoSupervisor,
            replyTo => SaveAccountRequest(DtoMapping.toInternalAccount(account, id), replyTo)
          )
        } yield res
      case req @ POST -> Root / "account" / "update" / id => for {
          body <- req.body.covary[IO].chunkAll.compile.toList
          bytes                       = body.flatMap(_.toList).toArray
          updateAccountRequestPayload = UpdateAccountRequestPayload.parseFrom(bytes)
          res <- sendApiCommandAndReturnResponse[AccountServiceQueryRequest](
            accountRepoSupervisor,
            replyTo =>
              UpdateAccountRequest(DtoMapping.toInternalAccount(updateAccountRequestPayload.account.get, id), replyTo)
          )
        } yield res
      case GET -> Root / "account" / id => for {
          res <- sendApiCommandAndReturnResponse[AccountServiceQueryRequest](
            accountRepoSupervisor,
            replyTo => GetAccountRequest(id, replyTo)
          )
        } yield res
      case DELETE -> Root / "account" / id => for {
          res <- sendApiCommandAndReturnResponse[AccountServiceQueryRequest](
            accountRepoSupervisor,
            replyTo => DeleteAccontRequest(id, replyTo)
          )
        } yield res
    }
  }
}
