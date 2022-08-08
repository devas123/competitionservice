package compman.compsrv.query.service

import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import cats.effect.IO
import compman.compsrv.logic.actors.behavior.api.AcademyApiActor.{AcademyApiCommand, GetAcademies, GetAcademy}
import compman.compsrv.logic.actors.behavior.api.CompetitionApiCommands._
import compman.compsrv.query.service.repository.Pagination
import compservice.model.protobuf.query.{
  GenerateCategoriesFromRestrictionsRequest,
  QueryServiceRequest,
  QueryServiceResponse
}
import org.http4s.{HttpRoutes, Response}
import org.http4s.dsl.Http4sDsl

import scala.concurrent.duration.DurationInt

object QueryHttpApiService {

  object QueryParameters {

    import org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher

    object SearchStringParamMatcher extends OptionalQueryParamDecoderMatcher[String]("searchString")
    object CategoryIdParameter      extends OptionalQueryParamDecoderMatcher[String]("categoryId")

    object StartAtParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("startAt")

    object LimitParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")
  }

  implicit val timeout: Timeout = 10.seconds

  type ServiceIO[A] = IO[A]

  private val dsl = Http4sDsl[ServiceIO]

  import dsl._

  def service(competitionApiActor: ActorRef[CompetitionApiCommand], academyApiActor: ActorRef[AcademyApiCommand])(
    implicit system: ActorSystem[_]
  ): HttpRoutes[ServiceIO] = {
    HttpRoutes.of[ServiceIO] {
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
      case req @ POST -> Root / "competition" / id / "info" => for {
          body <- req.body.covary[ServiceIO].chunkAll.compile.toList
          bytes   = body.flatMap(_.toList).toArray
          request = QueryServiceRequest.parseFrom(bytes)
          res <- sendApiCommandAndReturnResponse[CompetitionApiCommand](
            competitionApiActor,
            PutCompetitionInfo(competitionId = id, request = request)
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
      case GET -> Root / "competition" / id / "info" =>
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
      case GET -> Root / "academy" / id => sendApiCommandAndReturnResponse(academyApiActor, GetAcademy(id))
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
  }
  private def sendApiCommandAndReturnResponse[Command](
    apiActor: ActorRef[Command],
    apiCommandWithCallbackCreator: ActorRef[QueryServiceResponse] => Command
  )(implicit scheduler: Scheduler): ServiceIO[Response[ServiceIO]] = {
    IO.fromFuture[QueryServiceResponse](IO.pure(apiActor.ask(apiCommandWithCallbackCreator))).attempt.flatMap {
      case Left(_)      => InternalServerError()
      case Right(value) => Ok(value.toByteArray)
    }
  }
}
