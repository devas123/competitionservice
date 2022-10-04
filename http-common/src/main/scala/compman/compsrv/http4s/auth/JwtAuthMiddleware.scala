package compman.compsrv.http4s.auth

import cats.MonadThrow
import cats.data.{Kleisli, OptionT}
import cats.implicits._
import compman.compsrv.http4s.auth.JwtAuthTypes._
import org.http4s.{AuthedRoutes, Request}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.AuthMiddleware
import org.slf4j.LoggerFactory
import pdi.jwt.JwtClaim
import pdi.jwt.exceptions.JwtException

object JwtAuthMiddleware {

  private val log = LoggerFactory.getLogger(classOf[JwtAuthMiddleware.type])
  def apply[F[_]: MonadThrow, A](
    jwtAuth: JwtAuth,
    authenticate: JwtToken => JwtClaim => F[Option[A]]
  ): AuthMiddleware[F, A] = {
    val dsl = Http4sDsl[F]
    import dsl._

    val onFailure: AuthedRoutes[String, F] = Kleisli(req =>
      for {
        _    <- OptionT.liftF(log.error(s"Error while authenticating: $req").pure[F])
        resp <- OptionT.liftF(Forbidden(req.context))
      } yield resp
    )

    val authUser: Kleisli[F, Request[F], Either[String, A]] = Kleisli { request =>
      AuthHeaders.getBearerToken(request).fold("Bearer token not found".asLeft[A].pure[F]) { token =>
        jwtDecode[F](token, jwtAuth).flatMap(claim => authenticate(token)(claim)).map(_.fold("User ID is not found".asLeft[A])(_.asRight[String]))
          .recover { case e: JwtException => s"Invalid access token: $e".asLeft[A] }
      }
    }
    AuthMiddleware(authUser, onFailure)
  }
}
