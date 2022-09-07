package compman.compsrv.gateway.auth.jwt

import compman.compsrv.gateway.auth.jwt.JwtAuthTypes.JwtToken
import org.http4s.{AuthScheme, Request}
import org.http4s.headers.Authorization
import org.http4s.Credentials.Token

object AuthHeaders {
  def getBearerToken[F[_]](request: Request[F]): Option[JwtToken] = request.headers.get[Authorization]
    .collect { case Authorization(Token(AuthScheme.Bearer, token)) => JwtToken(token) }
}
