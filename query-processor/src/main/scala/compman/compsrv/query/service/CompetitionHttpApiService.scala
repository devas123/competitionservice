package compman.compsrv.query.service

import cats.data.Kleisli
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Request, Response}
import zio.Task
import zio.interop.catz._

object CompetitionHttpApiService {

  private val dsl = Http4sDsl[Task]
  import dsl._

  val service: Kleisli[Task, Request[Task], Response[Task]] = HttpRoutes.of[Task] {
    case GET -> Root => Ok("hello!")
  }.orNotFound
}