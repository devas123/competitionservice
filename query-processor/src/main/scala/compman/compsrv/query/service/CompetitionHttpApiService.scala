package compman.compsrv.query.service

import cats.data.Kleisli
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.query.actors.CompetitionApiActor.{ApiCommand, GetCompetitionInfoTemplate}
import compman.compsrv.query.actors.CompetitionProcessorActorRef
import org.http4s.{HttpRoutes, Request, Response}
import org.http4s.dsl.Http4sDsl
import zio.Task
import zio.duration.{durationInt, Duration}
import zio.interop.catz._
import zio.interop.catz.implicits._

object CompetitionHttpApiService {

  private val dsl = Http4sDsl[Task]
  import dsl._
  val timeout: Duration = 10.seconds
  val decoder           = new ObjectMapper()
  def service(apiActor: CompetitionProcessorActorRef[ApiCommand]): Kleisli[Task, Request[Task], Response[Task]] = HttpRoutes.of[Task] {
    case GET -> Root => Ok("hello!")
    case GET -> Root / "store" / "defaultrestrictions" => for {
        response <- apiActor ? GetCompetitionInfoTemplate
        m = Response[Task](body = fs2.Stream.fromIterator[Task](decoder.writeValueAsBytes(response).iterator, 1))
      } yield m

    case GET -> Root / "store" / "competition"                                                               => ???
    case GET -> Root / "store" / "competition" / "id"                                                        => ???
    case GET -> Root / "store" / "competition" / "id" / "infotemplate"                                       => ???
    case GET -> Root / "store" / "competition" / "id" / "schedule"                                           => ???
    case GET -> Root / "store" / "competition" / "id" / "dashboard"                                          => ???
    case GET -> Root / "store" / "competition" / "id" / "registration"                                       => ???
    case GET -> Root / "store" / "competition" / "id" / "category"                                           => ???
    case GET -> Root / "store" / "competition" / "id" / "category" / "id"                                    => ???
    case GET -> Root / "store" / "competition" / "id" / "category" / "id" / "fight"                          => ???
    case GET -> Root / "store" / "competition" / "id" / "category" / "id" / "fight" / "id"                   => ???
    case GET -> Root / "store" / "competition" / "id" / "category" / "id" / "fight" / "id" / "resultoptions" => ???
    case GET -> Root / "store" / "competition" / "id" / "category" / "id" / "stage"                          => ???
    case GET -> Root / "store" / "competition" / "id" / "category" / "id" / "stage" / "id"                   => ???
    case GET -> Root / "store" / "competition" / "id" / "category" / "id" / "stage" / "id" / "fight"         => ???
    case GET -> Root / "store" / "competition" / "id" / "mat"                                                => ???
    case GET -> Root / "store" / "competition" / "id" / "mat" / "id"                                         => ???
    case GET -> Root / "store" / "competition" / "id" / "mat" / "id" / "fight"                               => ???
    case GET -> Root / "store" / "competition" / "id" / "competitor"                                         => ???
    case GET -> Root / "store" / "competition" / "id" / "competitor" / "id"                                  => ???
  }.orNotFound
}
