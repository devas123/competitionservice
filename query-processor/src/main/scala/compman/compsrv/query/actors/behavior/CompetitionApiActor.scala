package compman.compsrv.query.actors.behavior

import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.{PeriodDTO, ScheduleDTO}
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.model.CompetitionProperties._
import zio.RIO

object CompetitionApiActor {
  sealed trait ApiCommand[+_]
  final case object GetDefaultRestrictions              extends ApiCommand[List[CategoryRestrictionDTO]]
  final case class GetAllCompetitions()                 extends ApiCommand[List[CompetitionPropertiesDTO]]
  final case class GetCompetitionProperties(id: String) extends ApiCommand[Option[CompetitionPropertiesDTO]]
  final case class GetCompetitionInfoTemplate(competitionId: String) extends ApiCommand[CompetitionInfoTemplate]
  final case class GetSchedule(competitionId: String)                extends ApiCommand[Option[ScheduleDTO]]
  final case class GetCompetitors(competitionId: String)             extends ApiCommand[List[CompetitorDTO]]
  final case class GetCompetitor(competitionId: String, competitorId: String) extends ApiCommand[List[CompetitorDTO]]
  final case class GetDashboard(competitionId: String)                        extends ApiCommand[List[PeriodDTO]]
  final case class GetMats(competitionId: String)                     extends ApiCommand[List[MatDescriptionDTO]]
  final case class GetMat(competitionId: String, matId: String)       extends ApiCommand[Option[MatDescriptionDTO]]
  final case class GetMatFights(competitionId: String, matId: String) extends ApiCommand[List[FightDescriptionDTO]]
  final case class GetRegistrationInfo(competitionId: String)         extends ApiCommand[Option[RegistrationInfoDTO]]
  final case class GetCategories(competitionId: String)               extends ApiCommand[List[CategoryDescriptorDTO]]
  final case class GetFightById(competitionId: String, categoryId: String, fightId: String)
      extends ApiCommand[Option[FightDescriptionDTO]]
  final case class GetCategory(competitionId: String, categoryId: String)
      extends ApiCommand[Option[CategoryDescriptorDTO]]
  final case class GetFightsByMatsByCategory(competitionId: String, categoryId: String)
      extends ApiCommand[List[FightDescriptionDTO]]
  final case class GetFightResulOptions(competitionId: String, categoryId: String, fightId: String)
      extends ApiCommand[List[FightResultDTO]]
  final case class GetStagesForCategory(competitionId: String, categoryId: String)
      extends ApiCommand[List[StageDescriptorDTO]]
  final case class GetStageById(competitionId: String, categoryId: String, stageId: String)
      extends ApiCommand[List[StageDescriptorDTO]]
  final case class GetStageFights(competitionId: String, categoryId: String, stageId: String)
      extends ApiCommand[List[FightDescriptionDTO]]

  case class ActorState()
  val initialState: ActorState = ActorState()
  def behavior[R](): ActorBehavior[R, ActorState, ApiCommand] = new ActorBehavior[R, ActorState, ApiCommand] {
    override def receive[A](
      context: Context[ApiCommand],
      actorConfig: ActorConfig,
      state: ActorState,
      command: ApiCommand[A],
      timers: Timers[R, ApiCommand]
    ): RIO[R, (ActorState, A)] = ???
  }
}
