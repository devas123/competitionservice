package compman.compsrv.query.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.query.actors.ActorRef
import compman.compsrv.query.actors.behavior.CompetitionApiActor._
import compman.compsrv.query.service.repository.Pagination
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, QueryParamDecoder, Response}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.{Duration, durationInt}
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.{RIO, Task}


object CompetitionHttpApiService {

  object QueryParameters {

    import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

    implicit val stringQueryParamDecoder: QueryParamDecoder[String] = QueryParamDecoder[String]
    implicit val intQueryParamDecoder: QueryParamDecoder[Int] =
      QueryParamDecoder[Int]

    object SearchStringParamMatcher extends OptionalQueryParamDecoderMatcher[String]("searchString")

    object StartAtParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("startAt")

    object LimitParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")
  }

  val timeout: Duration = 10.seconds
  val decoder = new ObjectMapper()

  type ServiceIO[A] = RIO[Clock with Blocking, A]

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._

  def service(apiActor: ActorRef[ApiCommand]) = HttpRoutes.of[ServiceIO] {
    case GET -> Root / "store" / "defaultrestrictions" => sendApiCommandAndReturnResponse(apiActor, GetDefaultRestrictions)
    case GET -> Root / "store" / "competition" => sendApiCommandAndReturnResponse(apiActor, GetAllCompetitions())
    case GET -> Root / "store" / "competition" / id => sendApiCommandAndReturnResponse(apiActor, GetCompetitionProperties(id))
    case GET -> Root / "store" / "competition" / id / "infotemplate" =>
      sendApiCommandAndReturnResponse(apiActor, GetCompetitionInfoTemplate(id))
    case GET -> Root / "store" / "competition" / id / "schedule" => sendApiCommandAndReturnResponse(apiActor, GetSchedule(id))
    case GET -> Root / "store" / "competition" / id / "dashboard" => sendApiCommandAndReturnResponse(apiActor, GetDashboard(id))
    case GET -> Root / "store" / "competition" / id / "registration" =>
      sendApiCommandAndReturnResponse(apiActor, GetRegistrationInfo(id))
    case GET -> Root / "store" / "competition" / id / "category" => sendApiCommandAndReturnResponse(apiActor, GetCategories(id))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId =>
      sendApiCommandAndReturnResponse(apiActor, GetCategory(id, categoryId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "fight" =>
      sendApiCommandAndReturnResponse(apiActor, GetFightsByMats(id, categoryId, 10))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "fight" / fightId =>
      sendApiCommandAndReturnResponse(apiActor, GetFightById(id, categoryId, fightId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "fight" / fightId / "resultoptions" =>
      sendApiCommandAndReturnResponse(apiActor, GetFightResulOptions(id, categoryId, fightId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "stage" =>
      sendApiCommandAndReturnResponse(apiActor, GetStagesForCategory(id, categoryId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "stage" / stageId =>
      sendApiCommandAndReturnResponse(apiActor, GetStageById(id, categoryId, stageId))
    case GET -> Root / "store" / "competition" / id / "category" / categoryId / "stage" / stageId / "fight" =>
      sendApiCommandAndReturnResponse(apiActor, GetStageFights(id, categoryId, stageId))
    case GET -> Root / "store" / "competition" / id / "mat" => sendApiCommandAndReturnResponse(apiActor, GetMats(id))
    case GET -> Root / "store" / "competition" / id / "mat" / matId => sendApiCommandAndReturnResponse(apiActor, GetMat(id, matId))
    case GET -> Root / "store" / "competition" / id / "mat" / matId / "fight" =>
      sendApiCommandAndReturnResponse(apiActor, GetMatFights(id, matId))
    case GET -> Root / "store" / "competition" / id / "competitor" :?
      QueryParameters.SearchStringParamMatcher(maybeSearchString) +&
        QueryParameters.StartAtParamMatcher(maybeStartAt) +&
        QueryParameters.LimitParamMatcher(maybeLimit) =>
      val pagination = for {
        s <- maybeStartAt
        lim <- maybeLimit
      } yield Pagination(s, lim, 0)
      sendApiCommandAndReturnResponse(apiActor, GetCompetitors(id, maybeSearchString, pagination))

    case GET -> Root / "store" / "competition" / id / "competitor" / competitorId =>
      sendApiCommandAndReturnResponse(apiActor, GetCompetitor(id, competitorId))
  }.orNotFound

  private def sendApiCommandAndReturnResponse[T](apiActor: ActorRef[ApiCommand], apiCommand: ApiCommand[T]): ServiceIO[Response[ServiceIO]] = {
    for {
      response <- (apiActor ? apiCommand).timeout(timeout)
      m = Response[ServiceIO](body = fs2.Stream.fromIterator[Task](decoder.writeValueAsBytes(response).iterator, 1))
    } yield m
  }
}
