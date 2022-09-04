package compman.compsrv

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import org.http4s.{HttpRoutes, Request}
import org.slf4j.Logger

package object http4s {
  def loggerMiddleware(service: HttpRoutes[IO])(log: Logger): HttpRoutes[IO] = {
    Kleisli { req: Request[IO] =>
      for {
        _ <- OptionT.liftF(IO {
          log.info(s"Request: $req")
        })
        response <- service(req)
        _ <- OptionT.liftF(IO {
          log.info(s"Response: $response")
        })
      } yield response
    }
  }
}
