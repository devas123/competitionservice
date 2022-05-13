package compman.compsrv.query.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.logic.actors.behavior.CompetitionApiActor._
import compman.compsrv.logic.actors.ActorRef
import compman.compsrv.model.commands.payload.AdjacencyList
import compman.compsrv.model.dto.competition.{CategoryDescriptorDTO, CategoryRestrictionDTO}
import compman.compsrv.query.serde.ObjectMapperFactory
import compman.compsrv.query.service.repository.Pagination
import compman.compsrv.query.service.CompetitionHttpApiService.Requests.GenerateCategoriesFromRestrictionsRequest
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.{durationInt, Duration}
import zio.interop.catz._
import zio.RIO
import zio.logging.Logging

object CompetitionHttpApiService {

  object Requests {
    final case class GenerateCategoriesFromRestrictionsRequest(
      restrictions: List[CategoryRestrictionDTO],
      idTrees: List[AdjacencyList],
      restrictionNames: List[String]
    )
  }

  object QueryParameters {

    import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

    object SearchStringParamMatcher extends OptionalQueryParamDecoderMatcher[String]("searchString")
    object CategoryIdParameter      extends OptionalQueryParamDecoderMatcher[String]("categoryId")

    object StartAtParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("startAt")

    object LimitParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")
  }

  val timeout: Duration     = 10.seconds
  val decoder: ObjectMapper = ObjectMapperFactory.createObjectMapper

  type ServiceIO[A] = RIO[Clock with Blocking with Logging, A]

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._

  def service(apiActor: ActorRef[ApiCommand]): HttpRoutes[ServiceIO] = HttpRoutes.of[ServiceIO] {
    case req @ POST -> Root / "generatecategories" / _ => for {
        body <- req.body.covary[ServiceIO].chunkAll.compile.toList
        bytes   = body.flatMap(_.toList).toArray
        request = decoder.readValue(bytes, classOf[GenerateCategoriesFromRestrictionsRequest])
        res <- sendApiCommandAndReturnResponse[List[CategoryDescriptorDTO]](
          apiActor,
          replyTo => GenerateCategoriesFromRestrictions(
            restrictions = request.restrictions,
            idTrees = request.idTrees,
            restrictionNames = request.restrictionNames
          )(replyTo)
        )
      } yield res
    case GET -> Root / "defaultfightresults" => sendApiCommandAndReturnResponse(apiActor, GetDefaultFightResults)
    case GET -> Root / "defaultrestrictions" => sendApiCommandAndReturnResponse(apiActor, GetDefaultRestrictions)
    case GET -> Root / "competition"         => sendApiCommandAndReturnResponse(apiActor, GetAllCompetitions)
    case GET -> Root / "competition" / id    => sendApiCommandAndReturnResponse(apiActor, GetCompetitionProperties(id))
    case GET -> Root / "competition" / id / "fight" =>
      sendApiCommandAndReturnResponse(apiActor, GetFightIdsByCategoryIds(id))
    case GET -> Root / "competition" / id / "category" / categoryId / "fight" / fightId =>
      sendApiCommandAndReturnResponse(apiActor, GetFightById(id, categoryId, fightId))
    case GET -> Root / "competition" / id / "infotemplate" =>
      sendApiCommandAndReturnResponse(apiActor, GetCompetitionInfoTemplate(id))
    case GET -> Root / "competition" / id / "schedule"  => sendApiCommandAndReturnResponse(apiActor, GetSchedule(id))
    case GET -> Root / "competition" / id / "dashboard" => sendApiCommandAndReturnResponse(apiActor, GetDashboard(id))
    case GET -> Root / "competition" / id / "reginfo" =>
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
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" / stageId / "resultoptions" =>
      sendApiCommandAndReturnResponse(apiActor, GetFightResulOptions(id, categoryId, stageId))
    case GET -> Root / "competition" / id / "mat" => sendApiCommandAndReturnResponse(apiActor, GetMats(id))
    case GET -> Root / "competition" / id / "mat" / matId =>
      sendApiCommandAndReturnResponse(apiActor, GetMat(id, matId))
    case GET -> Root / "competition" / id / "mat" / matId / "fight" =>
      sendApiCommandAndReturnResponse(apiActor, GetMatFights(id, matId))
    case GET -> Root / "competition" / id / "period" / periodId / "fight" =>
      sendApiCommandAndReturnResponse(apiActor, GetPeriodFightsByMats(id, periodId, 10))
    case GET -> Root / "competition" / id / "period" / periodId / "mat" =>
      sendApiCommandAndReturnResponse(apiActor, GetPeriodMats(id, periodId))
    case GET -> Root / "competition" / id / "competitor" :?
        QueryParameters.SearchStringParamMatcher(maybeSearchString) +& QueryParameters.StartAtParamMatcher(
          maybeStartAt
        ) +& QueryParameters.LimitParamMatcher(maybeLimit) +& QueryParameters.CategoryIdParameter(maybeCategoryId) =>
      val pagination = for {
        s   <- maybeStartAt
        lim <- maybeLimit
      } yield Pagination(s, lim, 0)
      sendApiCommandAndReturnResponse(apiActor, GetCompetitors(id, maybeCategoryId, maybeSearchString, pagination))

    case GET -> Root / "competition" / id / "competitor" / competitorId =>
      sendApiCommandAndReturnResponse(apiActor, GetCompetitor(id, competitorId))

    case GET -> Root / "competition" / id / "competitor" / competitorId =>
      sendApiCommandAndReturnResponse(apiActor, GetCompetitor(id, competitorId))
  }

  private def sendApiCommandAndReturnResponse[Resp](
    apiActor: ActorRef[ApiCommand],
    apiCommand: ActorRef[Resp] => ApiCommand
  ): ServiceIO[Response[ServiceIO]] = {
    import compman.compsrv.logic.actors.patterns.Patterns._
    for {
      response <- (apiActor ? apiCommand).onError(err => Logging.error(s"Error while getting response: $err"))
      bytes = decoder.writeValueAsBytes(response)
      _ <- Logging.debug(s"Sending bytes: ${new String(bytes)}")
      m <- Ok(bytes)
    } yield m
  }
}
