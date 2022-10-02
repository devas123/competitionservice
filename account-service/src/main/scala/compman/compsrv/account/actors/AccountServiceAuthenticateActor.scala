package compman.compsrv.account.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.data.EitherT
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import compman.compsrv.account.config.AuthenticationConfig
import compman.compsrv.account.model.InternalAccount
import compman.compsrv.account.service.AccountRepository
import compman.compsrv.http4s.auth.JwtAuthTypes
import compman.compsrv.http4s.auth.JwtAuthTypes.JwtSecretKey
import compservice.model.protobuf.account.AccountServiceResponse
import compservice.model.protobuf.model.{AuthenticationResponsePayload, ErrorResponse}
import io.github.nremond.SecureHash
import pdi.jwt.JwtAlgorithm.HS512
import pdi.jwt.JwtClaim

import scala.util.Try

object AccountServiceAuthenticateActor {
  sealed trait AccountServiceAuthenticateActorApi
  case class Authenticate(username: String /* email */, password: String)(val replyTo: ActorRef[AccountServiceResponse])
      extends AccountServiceAuthenticateActorApi

  def behavior(
    repository: AccountRepository,
    authConfig: AuthenticationConfig
  ): Behavior[AccountServiceAuthenticateActorApi] = Behaviors.setup { _ =>
    implicit val runtime: IORuntime = IORuntime.global
    Behaviors.receiveMessage { case req @ Authenticate(username, password) =>
      val effect = for {
//        user          <- EitherT.fromOptionF(repository.getAccountByUserName(username), "User missing")
        user          <- EitherT.pure[IO, String](InternalAccount("1", "Valera", "Protas", username, None, SecureHash.createHash("bananas")))
        passwordValid <- EitherT.liftF(IO { SecureHash.validatePassword(password, user.password) })
        token <- EitherT {
          if (passwordValid) JwtAuthTypes.jwtEncode[IO](JwtClaim(), JwtSecretKey(authConfig.jwtSecretKey), HS512)
            .map(Right(_))
          else { IO(Left("Wrong password")) }
        }
      } yield token
      val result = Try { effect.value.unsafeRunSync() }.fold(
        ex => AccountServiceResponse().withErrorResponse(ErrorResponse(Some(ex.getMessage))),
        {
          case Left(value) => AccountServiceResponse().withErrorResponse(ErrorResponse(Some(value)))
          case Right(value) => AccountServiceResponse()
              .withAuthenticationResponsePayload(AuthenticationResponsePayload(token = value.value))
        }
      )
      req.replyTo ! result
      Behaviors.same
    }

  }
}
