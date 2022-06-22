package compman.compsrv.query.service

import compman.compsrv.logic.actors.behavior.api.CompetitionApiActor._
import compman.compsrv.logic.actors.ActorRef
import compman.compsrv.logic.actors.behavior.api.AcademyApiActor.{AcademyApiCommand, GetAcademies, GetAcademy}
import compman.compsrv.query.service.repository.Pagination
import compservice.model.protobuf.query.{GenerateCategoriesFromRestrictionsRequest, QueryServiceResponse}
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.{durationInt, Duration}
import zio.interop.catz._
import zio.RIO
import zio.logging.Logging

object QueryHttpApiService {

  object QueryParameters {

    import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

    object SearchStringParamMatcher extends OptionalQueryParamDecoderMatcher[String]("searchString")
    object CategoryIdParameter      extends OptionalQueryParamDecoderMatcher[String]("categoryId")

    object StartAtParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("startAt")

    object LimitParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")
  }

  val timeout: Duration = 10.seconds

  type ServiceIO[A] = RIO[Clock with Blocking with Logging, A]

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._

  def service(
    competitionApiActor: ActorRef[CompetitionApiCommand],
    academyApiActor: ActorRef[AcademyApiCommand]
  ): HttpRoutes[ServiceIO] = HttpRoutes.of[ServiceIO] {
    case req @ POST -> Root / "generatecategories" / _ => for {
        body <- req.body.covary[ServiceIO].chunkAll.compile.toList
        bytes   = body.flatMap(_.toList).toArray
        request = GenerateCategoriesFromRestrictionsRequest.parseFrom(bytes)
        res <- sendApiCommandAndReturnResponse[CompetitionApiCommand](
          competitionApiActor,
          GenerateCategoriesFromRestrictions(
            restrictions = request.restrictions.toList,
            idTrees = request.idTrees.toList,
            restrictionNames = request.restrictionNames.toList
          )
        )
      } yield res
    case GET -> Root / "defaultfightresults" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetDefaultFightResults)
    case GET -> Root / "defaultrestrictions" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetDefaultRestrictions)
    case GET -> Root / "competition" => sendApiCommandAndReturnResponse(competitionApiActor, GetAllCompetitions)
    case GET -> Root / "competition" / id =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCompetitionProperties(id))
    case GET -> Root / "competition" / id / "fight" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetFightIdsByCategoryIds(id))
    case GET -> Root / "competition" / id / "fight" / fightId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetFightById(id, fightId))
    case GET -> Root / "competition" / id / "infotemplate" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCompetitionInfoTemplate(id))
    case GET -> Root / "competition" / id / "schedule" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetSchedule(id))
    case GET -> Root / "competition" / id / "dashboard" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetDashboard(id))
    case GET -> Root / "competition" / id / "reginfo" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetRegistrationInfo(id))
    case GET -> Root / "competition" / id / "category" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCategories(id))
    case GET -> Root / "competition" / id / "category" / categoryId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCategory(id, categoryId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetStagesForCategory(id, categoryId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" / stageId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetStageById(id, categoryId, stageId))
    case GET -> Root / "competition" / id / "category" / categoryId / "stage" / stageId / "fight" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetStageFights(id, categoryId, stageId))
    case GET -> Root / "competition" / id / "stage" / stageId / "resultoptions" =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetFightResulOptions(id, stageId))
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
      sendApiCommandAndReturnResponse(
        competitionApiActor,
        GetCompetitors(id, maybeCategoryId, maybeSearchString, pagination)
      )

    case GET -> Root / "competition" / id / "competitor" / competitorId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCompetitor(id, competitorId))

    case GET -> Root / "competition" / id / "competitor" / competitorId =>
      sendApiCommandAndReturnResponse(competitionApiActor, GetCompetitor(id, competitorId))
    case GET -> Root / "academy" / id =>
      sendApiCommandAndReturnResponse(academyApiActor, GetAcademy(id))
    case GET -> Root / "academy" :?
        QueryParameters.SearchStringParamMatcher(maybeSearchString) +& QueryParameters.StartAtParamMatcher(
          maybeStartAt
        ) +& QueryParameters.LimitParamMatcher(maybeLimit) => sendApiCommandAndReturnResponse(
        academyApiActor,
        GetAcademies(
          maybeSearchString,
          maybeStartAt.flatMap(s => maybeLimit.map(l => (s, l))).map(sl => Pagination(sl._1, sl._2, 0))
        )
      )
  }

  private def sendApiCommandAndReturnResponse[Command](
    apiActor: ActorRef[Command],
    apiCommandWithCallbackCreator: ActorRef[QueryServiceResponse] => Command
  ): ServiceIO[Response[ServiceIO]] = {
    import compman.compsrv.logic.actors.patterns.Patterns._
    implicit val timeout: Duration = 10.seconds

    for {
      response <- (apiActor ? apiCommandWithCallbackCreator)
        .onError(err => Logging.error(s"Error while getting response: $err"))
      bytes = response.map(_.toByteArray)
      _ <- Logging.debug(s"Sending bytes: ${new String(bytes.getOrElse(Array.empty))}")
      m <- bytes match {
        case Some(value) => Ok(value)
        case None        => RequestTimeout()
      }
    } yield m
  }
}
