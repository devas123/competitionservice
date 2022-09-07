package compman.compsrv.gateway.auth.jwt

import cats.MonadThrow
import cats.data.{Kleisli, OptionT}
import cats.implicits._
import compman.compsrv.gateway.auth.jwt.JwtAuthTypes._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.http4s.{AuthedRoutes, Request}
import pdi.jwt.JwtClaim
import pdi.jwt.exceptions.JwtException

object JwtAuthMiddleware {
  def apply[F[_]: MonadThrow, A](
    jwtAuth: JwtAuth,
    authenticate: JwtToken => JwtClaim => F[Option[A]]
  ): AuthMiddleware[F, A] = {
    val dsl = Http4sDsl[F]
    import dsl._

    val onFailure: AuthedRoutes[String, F] = Kleisli(req => OptionT.liftF(Forbidden(req.context)))

    val authUser: Kleisli[F, Request[F], Either[String, A]] = Kleisli { request =>
      AuthHeaders.getBearerToken(request).fold("Bearer token not found".asLeft[A].pure[F]) { token =>
        jwtDecode[F](token, jwtAuth).flatMap(authenticate(token)).map(_.fold("not found".asLeft[A])(_.asRight[String]))
          .recover { case _: JwtException => "Invalid access token".asLeft[A] }
      }
    }
    AuthMiddleware(authUser, onFailure)
  }
}
