package compman.compsrv.query.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.logic.actors.behavior.api.CompetitionApiActor._
import compman.compsrv.logic.actors.ActorRef
import compman.compsrv.logic.actors.behavior.api.AcademyApiActor.{AcademyApiCommand, GetAcademies}
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

  def service(competitionApiActor: ActorRef[CompetitionApiCommand], academyApiActor: ActorRef[AcademyApiCommand]): HttpRoutes[ServiceIO] = HttpRoutes.of[ServiceIO] {
    case req @ POST -> Root / "generatecategories" / _ => for {
        body <- req.body.covary[ServiceIO].chunkAll.compile.toList
        bytes   = body.flatMap(_.toList).toArray
        request = decoder.readValue(bytes, classOf[GenerateCategoriesFromRestrictionsRequest])
        res <- sendApiCommandAndReturnResponse[List[CategoryDescriptorDTO], CompetitionApiCommand](
          competitionApiActor,
          replyTo => GenerateCategoriesFromRestrictions(
            restrictions = request.restrictions,
            idTrees = request.idTrees,
            restrictionNames = request.restrictionNames
          )(replyTo)
        )
      } yield res
    case GET -> Root / "defaultfightresults" => sendApiCommandAndReturnResponse(competitionApiActor, GetDefaultFightResults)
    case GET -> Root / "defaultrestrictions" => sendApiCommandAndReturnResponse(competitionApiActor, GetDefaultRestrictions)
    case GET -> Root / "competition"         => sendApiCommandAndReturnResponse(competitionApiActor, GetAllCompetitions)
    case GET -> Root / "competition" / id    => sendApiCommandAndReturnResponse(competitionApiActor, GetCompetitionProperties(id))
    case GET -> Root / "competition" / id / "fight" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetFightIdsByCategoryIds(id))
    case GET -> Root / "competition" / id / "category" / categoryId / "fight" / fightId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetFightById(id, categoryId, fightId))
    case GET -> Root / "competition" / id / "infotemplate" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCompetitionInfoTemplate(id))
    case GET -> Root / "competition" / id / "schedule"  => sendApiCommandAndReturnResponse(competitionApiActor, GetSchedule(id))
    case GET -> Root / "competition" / id / "dashboard" => sendApiCommandAndReturnResponse(competitionApiActor, GetDashboard(id))
    case GET -> Root / "competition" / id / "reginfo" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetRegistrationInfo(id))
    case GET -> Root / "competition" / id / "category" => sendApiCommandAndReturnResponse(competitionApiActor, GetCategories(id))
    case GET -> Root / "competition" / id / "category" / categoryId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCategory(id, categoryId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetStagesForCategory(id, categoryId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" / stageId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetStageById(id, categoryId, stageId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" / stageId / "fight" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetStageFights(id, categoryId, stageId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" / stageId / "resultoptions" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetFightResulOptions(id, categoryId, stageId))
    case GET -> Root / "competition" / id / "mat" => sendApiCommandAndReturnResponse(competitionApiActor, GetMats(id))
    case GET -> Root / "competition" / id / "mat" / matId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetMat(id, matId))
    case GET -> Root / "competition" / id / "mat" / matId / "fight" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetMatFights(id, matId))
    case GET -> Root / "competition" / id / "period" / periodId / "fight" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetPeriodFightsByMats(id, periodId, 10))
    case GET -> Root / "competition" / id / "period" / periodId / "mat" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetPeriodMats(id, periodId))
    case GET -> Root / "competition" / id / "competitor" :?
        QueryParameters.SearchStringParamMatcher(maybeSearchString) +& QueryParameters.StartAtParamMatcher(
          maybeStartAt
        ) +& QueryParameters.LimitParamMatcher(maybeLimit) +& QueryParameters.CategoryIdParameter(maybeCategoryId) =>
      val pagination = for {
        s   <- maybeStartAt
        lim <- maybeLimit
      } yield Pagination(s, lim, 0)
      sendApiCommandAndReturnResponse(competitionApiActor, GetCompetitors(id, maybeCategoryId, maybeSearchString, pagination))

    case GET -> Root / "competition" / id / "competitor" / competitorId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCompetitor(id, competitorId))

    case GET -> Root / "competition" / id / "competitor" / competitorId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCompetitor(id, competitorId))
    case GET -> Root / "academy" =>
      sendApiCommandAndReturnResponse(academyApiActor, GetAcademies(None, None))
  }

  private def sendApiCommandAndReturnResponse[Resp, Command](
    apiActor: ActorRef[Command],
    apiCommand: ActorRef[Resp] => Command
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
