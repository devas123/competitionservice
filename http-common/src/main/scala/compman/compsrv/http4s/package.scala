package compman.compsrv

import cats.data.Kleisli
import cats.effect.IO
import cats.Monad
import cats.implicits._
import org.http4s.{Http, Request}
import org.slf4j.Logger

package object http4s {
  def loggerMiddleware[G[_]: Monad](service: Http[G, IO])(log: Logger): Http[G, IO] = Kleisli { req: Request[IO] =>
    for {
      _        <- log.info(s"Request: $req").pure[G]
      response <- service(req)
      _        <- log.info(s"Response: $response").pure[G]
    } yield response
  }
}
