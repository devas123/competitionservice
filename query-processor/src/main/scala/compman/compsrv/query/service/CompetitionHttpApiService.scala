package compman.compsrv.query.service

import cats.data.Kleisli
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.query.actors.behavior.CompetitionApiActor._
import compman.compsrv.query.actors.ActorRef
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
  def service(apiActor: ActorRef[ApiCommand]): Kleisli[Task, Request[Task], Response[Task]] = HttpRoutes.of[Task] {
    case GET -> Root                                   => Ok("hello!")
    case GET -> Root / "store" / "defaultrestrictions" => sendApiCommand(apiActor, GetDefaultRestrictions)
    case GET -> Root / "store" / "competition"         => sendApiCommand(apiActor, GetAllCompetitions())
    case GET -> Root / "store" / "competition" / id    => sendApiCommand(apiActor, GetCompetitionProperties(id))
    case GET -> Root / "store" / "competition" / id / "infotemplate" =>
      sendApiCommand(apiActor, GetCompetitionInfoTemplate(id))
    case GET -> Root / "store" / "competition" / id / "schedule"  => sendApiCommand(apiActor, GetSchedule(id))
    case GET -> Root / "store" / "competition" / id / "dashboard" => sendApiCommand(apiActor, GetDashboard(id))
    case GET -> Root / "store" / "competition" / id / "registration" =>
      sendApiCommand(apiActor, GetRegistrationInfo(id))
    case GET -> Root / "store" / "competition" / id / "category" => sendApiCommand(apiActor, GetCategories(id))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId =>
      sendApiCommand(apiActor, GetCategory(id, categoryId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "fight" =>
      sendApiCommand(apiActor, GetFightsByMats(id, categoryId, 10))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "fight" / fightId =>
      sendApiCommand(apiActor, GetFightById(id, categoryId, fightId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "fight" / fightId / "resultoptions" =>
      sendApiCommand(apiActor, GetFightResulOptions(id, categoryId, fightId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "stage" =>
      sendApiCommand(apiActor, GetStagesForCategory(id, categoryId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "stage" / stageId =>
      sendApiCommand(apiActor, GetStageById(id, categoryId, stageId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "stage" / stageId / "fight" =>
      sendApiCommand(apiActor, GetStageFights(id, categoryId, stageId))
    case GET -> Root / "store" / "competition" / id / "mat"         => sendApiCommand(apiActor, GetMats(id))
    case GET -> Root / "store" / "competition" / id / "mat" / matId => sendApiCommand(apiActor, GetMat(id, matId))
    case GET -> Root / "store" / "competition" / id / "mat" / matId / "fight" =>
      sendApiCommand(apiActor, GetMatFights(id, matId))
    case GET -> Root / "store" / "competition" / id / "competitor" => sendApiCommand(apiActor, GetCompetitors(id))
    case GET -> Root / "store" / "competition" / id / "competitor" / competitorId =>
      sendApiCommand(apiActor, GetCompetitor(id, competitorId))
  }.orNotFound

  private def sendApiCommand[T](apiActor: ActorRef[ApiCommand], apiCommand: ApiCommand[T]) = {
    for {
      response <- apiActor ? apiCommand
      m = Response[Task](body = fs2.Stream.fromIterator[Task](decoder.writeValueAsBytes(response).iterator, 1))
    } yield m
  }
}
