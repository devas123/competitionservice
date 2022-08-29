package compman.compsrv.account.service

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.util.Timeout
import cats.effect.IO
import compman.compsrv.account.actors.AccountRepositorySupervisorActor._
import compman.compsrv.account.model.mapping.DtoMapping
import compservice.model.protobuf.account._
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.duration.DurationInt

object HttpServer {

  private val logger            = LoggerFactory.getLogger(classOf[HttpServer.type])
  implicit val timeout: Timeout = 3.seconds
  private val dsl               = Http4sDsl[IO]

  import dsl._

  private def sendApiCommandAndReturnResponse[Command](
    apiActor: ActorRef[Command],
    apiCommandWithCallbackCreator: ActorRef[AccountServiceResponse] => Command
  )(implicit scheduler: Scheduler): IO[Response[IO]] = {
    IO.fromFuture[AccountServiceResponse](IO.pure(apiActor.ask(apiCommandWithCallbackCreator))).attempt.flatMap {
      case Left(_)      => InternalServerError()
      case Right(value) => Ok(value.toByteArray)
    }
  }
  def routes(
    accountRepoSupervisor: ActorRef[AccountServiceQueryRequest]
  )(implicit system: ActorSystem[_]): HttpRoutes[IO] = {
    import akka.actor.typed.scaladsl.AskPattern.schedulerFromActorSystem

    HttpRoutes.of[IO] {
      case req @ POST -> Root / "account" => for {
          body <- req.body.covary[IO].chunkAll.compile.toList
          bytes                 = body.flatMap(_.toList).toArray
          id                    = UUID.randomUUID().toString
          accountRequestPayload = AddAccountRequestPayload.parseFrom(bytes)
          account = Account(
            id,
            firstName = accountRequestPayload.firstName,
            lastName = accountRequestPayload.lastName,
            email = accountRequestPayload.email,
            birthDate = accountRequestPayload.birthDate
          )
          res <- sendApiCommandAndReturnResponse[AccountServiceQueryRequest](
            accountRepoSupervisor,
            replyTo => SaveAccountRequest(DtoMapping.toInternalAccount(account), replyTo)
          )
        } yield res
      case req @ POST -> Root / "account" / "update" => for {
          body <- req.body.covary[IO].chunkAll.compile.toList
          bytes                       = body.flatMap(_.toList).toArray
          updateAccountRequestPayload = UpdateAccountRequestPayload.parseFrom(bytes)
          res <- sendApiCommandAndReturnResponse[AccountServiceQueryRequest](
            accountRepoSupervisor,
            replyTo =>
              UpdateAccountRequest(DtoMapping.toInternalAccount(updateAccountRequestPayload.account.get), replyTo)
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
