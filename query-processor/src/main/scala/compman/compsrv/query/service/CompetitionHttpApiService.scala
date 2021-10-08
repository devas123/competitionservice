package compman.compsrv.query.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.query.actors.ActorRef
import compman.compsrv.query.actors.behavior.CompetitionApiActor._
import compman.compsrv.query.sede.ObjectMapperFactory
import compman.compsrv.query.service.repository.Pagination
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.{durationInt, Duration}
import zio.interop.catz._
import zio.RIO
import zio.logging.Logging

object CompetitionHttpApiService {

  object QueryParameters {

    import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

    object SearchStringParamMatcher extends OptionalQueryParamDecoderMatcher[String]("searchString")

    object StartAtParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("startAt")

    object LimitParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")
  }

  val timeout: Duration     = 10.seconds
  val decoder: ObjectMapper = ObjectMapperFactory.createObjectMapper

  type ServiceIO[A] = RIO[Clock with Blocking with Logging, A]

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._

  def service(apiActor: ActorRef[ApiCommand]): HttpRoutes[ServiceIO] = HttpRoutes.of[ServiceIO] {
    case req @ POST -> Root / "generatecategories" / _ =>
      for {
        body <- req.body.covary[ServiceIO].chunkAll.compile.toList
        bytes = body.flatMap(_.toList).toArray
        res <- sendApiCommandAndReturnResponse(apiActor, decoder.readValue(bytes, classOf[GenerateCategoriesFromRestrictions]))
      } yield res
    case GET -> Root / "defaultrestrictions" => sendApiCommandAndReturnResponse(apiActor, GetDefaultRestrictions)
    case GET -> Root / "competition"         => sendApiCommandAndReturnResponse(apiActor, GetAllCompetitions)
    case GET -> Root / "competition" / id    => sendApiCommandAndReturnResponse(apiActor, GetCompetitionProperties(id))
    case GET -> Root / "competition" / id / "fight" / fightId =>
      sendApiCommandAndReturnResponse(apiActor, GetFightById(id, fightId))
    case GET -> Root / "competition" / id / "infotemplate" =>
      sendApiCommandAndReturnResponse(apiActor, GetCompetitionInfoTemplate(id))
    case GET -> Root / "competition" / id / "schedule"  => sendApiCommandAndReturnResponse(apiActor, GetSchedule(id))
    case GET -> Root / "competition" / id / "dashboard" => sendApiCommandAndReturnResponse(apiActor, GetDashboard(id))
    case GET -> Root / "competition" / id / "registration" =>
      sendApiCommandAndReturnResponse(apiActor, GetRegistrationInfo(id))
    case GET -> Root / "competition" / id / "category" => sendApiCommandAndReturnResponse(apiActor, GetCategories(id))
    case GET -> Root / "competition" / id / "category" / categoryId =>
      sendApiCommandAndReturnResponse(apiActor, GetCategory(id, categoryId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" =>
      sendApiCommandAndReturnResponse(apiActor, GetStagesForCategory(id, categoryId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" / stageId =>
      sendApiCommandAndReturnResponse(apiActor, GetStageById(id, categoryId, stageId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" / stageId / "fight" =>
      sendApiCommandAndReturnResponse(apiActor, GetStageFights(id, categoryId, stageId))
    case GET -> Root / "competition" / id / "stage" / stageId / "resultoptions" =>
      sendApiCommandAndReturnResponse(apiActor, GetFightResulOptions(id, stageId))
    case GET -> Root / "competition" / id / "mat" => sendApiCommandAndReturnResponse(apiActor, GetMats(id))
    case GET -> Root / "competition" / id / "mat" / matId =>
      sendApiCommandAndReturnResponse(apiActor, GetMat(id, matId))
    case GET -> Root / "competition" / id / "mat" / matId / "fight" =>
      sendApiCommandAndReturnResponse(apiActor, GetMatFights(id, matId))
    case GET -> Root / "competition" / id / "period" / periodId / "fight" =>
      sendApiCommandAndReturnResponse(apiActor, GetFightsByMats(id, periodId, 10))
    case GET -> Root / "competition" / id / "competitor" :? QueryParameters.SearchStringParamMatcher(
          maybeSearchString
        ) +& QueryParameters.StartAtParamMatcher(maybeStartAt) +& QueryParameters.LimitParamMatcher(maybeLimit) =>
      val pagination = for {
        s   <- maybeStartAt
        lim <- maybeLimit
      } yield Pagination(s, lim, 0)
      sendApiCommandAndReturnResponse(apiActor, GetCompetitors(id, maybeSearchString, pagination))

    case GET -> Root / "competition" / id / "competitor" / competitorId =>
      sendApiCommandAndReturnResponse(apiActor, GetCompetitor(id, competitorId))
  }

  private def sendApiCommandAndReturnResponse[T](
    apiActor: ActorRef[ApiCommand],
    apiCommand: ApiCommand[T]
  ): ServiceIO[Response[ServiceIO]] = {
    for {
      response <- (apiActor ? apiCommand).onError(err => Logging.error(s"Error while getting response: $err"))
      bytes = decoder.writeValueAsBytes(response)
      _ <- Logging.debug(s"Sending bytes: ${new String(bytes)}")
      m <- Ok(bytes)
    } yield m
  }
}
