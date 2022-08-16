package compman.compsrv.logic.actors.behavior.api

import akka.actor.typed.ActorRef
import compman.compsrv.query.service.repository.Pagination
import compservice.model.protobuf.model.{AdjacencyList, CategoryRestriction}
import compservice.model.protobuf.query.{QueryServiceRequest, QueryServiceResponse}

object CompetitionApiCommands {
  sealed trait CompetitionApiCommand {
    type responseType = QueryServiceResponse
    val replyTo: ActorRef[responseType]
  }

  final case class GetDefaultRestrictions(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetDefaultFightResults(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetAllCompetitions(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GenerateCategoriesFromRestrictions(
    restrictions: List[CategoryRestriction],
    idTrees: List[AdjacencyList],
    restrictionNames: List[String]
  )(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetCompetitionProperties(id: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetCompetitionInfoTemplate(competitionId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetSchedule(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetCompetitors(
    competitionId: String,
    categoryId: Option[String],
    searchString: Option[String],
    pagination: Option[Pagination]
  )(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetCompetitor(competitionId: String, competitorId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetDashboard(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetMats(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetPeriodMats(competitionId: String, periodId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetMat(competitionId: String, matId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetMatFights(competitionId: String, matId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetRegistrationInfo(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetCategories(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetFightById(competitionId: String, fightId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetFightIdsByCategoryIds(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand

  final case class GetCategory(competitionId: String, categoryId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetPeriodFightsByMats(competitionId: String, periodId: String, limit: Int)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetFightResulOptions(competitionId: String, stageId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetStagesForCategory(competitionId: String, categoryId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetStageById(competitionId: String, categoryId: String, stageId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class GetStageFights(competitionId: String, categoryId: String, stageId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class PutCompetitionInfo(competitionId: String, request: QueryServiceRequest)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class PutCompetitionImage(competitionId: String, request: QueryServiceRequest)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand
  final case class GetCompetitionImage(competitionId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

  final case class RemoveCompetitionImage(competitionId: String, request: QueryServiceRequest)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand

}
