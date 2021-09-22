package compman.compsrv.query.actors.behavior

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.{PeriodDTO, ScheduleDTO}
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.model.CompetitionProperties._
import compman.compsrv.query.model.{Category, CompetitionProperties, Competitor, Fight, FightResult, Mat, Period, RegistrationInfo, Restriction, StageDescriptor}
import compman.compsrv.query.service.repository.CompetitionQueryOperations
import io.getquill.CassandraZioSession
import zio.{RIO, Tag}
import zio.logging.Logging

object CompetitionApiActor {

  case class Live(cassandraZioSession: CassandraZioSession) extends ActorContext {
    implicit val loggingLive: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .live(cassandraZioSession)
  }

  object Test extends ActorContext {
    implicit val loggingLive: CompetitionLogging.Service[LIO]                = CompetitionLogging.Live.live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations.test()
  }

  trait ActorContext {
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO]
  }

  sealed trait ApiCommand[+_]
  final case object GetDefaultRestrictions              extends ApiCommand[List[Restriction]]
  final case class GetAllCompetitions()                 extends ApiCommand[List[CompetitionProperties]]
  final case class GetCompetitionProperties(id: String) extends ApiCommand[Option[CompetitionProperties]]
  final case class GetCompetitionInfoTemplate(competitionId: String) extends ApiCommand[CompetitionInfoTemplate]
  final case class GetSchedule(competitionId: String)                extends ApiCommand[List[Period]]
  final case class GetCompetitors(competitionId: String)             extends ApiCommand[List[Competitor]]
  final case class GetCompetitor(competitionId: String, competitorId: String) extends ApiCommand[List[Competitor]]
  final case class GetDashboard(competitionId: String)                        extends ApiCommand[List[Period]]
  final case class GetMats(competitionId: String)                     extends ApiCommand[List[Mat]]
  final case class GetMat(competitionId: String, matId: String)       extends ApiCommand[Option[Mat]]
  final case class GetMatFights(competitionId: String, matId: String) extends ApiCommand[List[Fight]]
  final case class GetRegistrationInfo(competitionId: String)         extends ApiCommand[Option[RegistrationInfo]]
  final case class GetCategories(competitionId: String)               extends ApiCommand[List[Category]]
  final case class GetFightById(competitionId: String, categoryId: String, fightId: String)
      extends ApiCommand[Option[Fight]]
  final case class GetCategory(competitionId: String, categoryId: String)
      extends ApiCommand[Option[Category]]
  final case class GetFightsByMats(competitionId: String, categoryId: String, limit: Int)
      extends ApiCommand[Map[String, List[String]]]
  final case class GetFightResulOptions(competitionId: String, categoryId: String, fightId: String)
      extends ApiCommand[List[FightResult]]
  final case class GetStagesForCategory(competitionId: String, categoryId: String)
      extends ApiCommand[List[StageDescriptor]]
  final case class GetStageById(competitionId: String, categoryId: String, stageId: String)
      extends ApiCommand[List[StageDescriptor]]
  final case class GetStageFights(competitionId: String, categoryId: String, stageId: String)
      extends ApiCommand[List[Fight]]

  case class ActorState()
  val initialState: ActorState = ActorState()
  def behavior[R: Tag](ctx: ActorContext): ActorBehavior[R with Logging, ActorState, ApiCommand] =
    new ActorBehavior[R with Logging, ActorState, ApiCommand] {
      import ctx._

      override def receive[A](
        context: Context[ApiCommand],
        actorConfig: ActorConfig,
        state: ActorState,
        command: ApiCommand[A],
        timers: Timers[R with Logging, ApiCommand]
      ): RIO[R with Logging, (ActorState, A)] = command match {
        case GetDefaultRestrictions => ???
        case GetAllCompetitions() => CompetitionQueryOperations[LIO].getCompetitionProperties("")
            .map(res => (state, res.asInstanceOf[A]))
        case GetCompetitionProperties(id)                             => ???
        case GetCompetitionInfoTemplate(competitionId)                => ???
        case GetSchedule(competitionId)                               => ???
        case GetCompetitors(competitionId)                            => ???
        case GetCompetitor(competitionId, competitorId)               => ???
        case GetDashboard(competitionId)                              => ???
        case GetMats(competitionId)                                   => ???
        case GetMat(competitionId, matId)                             => ???
        case GetMatFights(competitionId, matId)                       => ???
        case GetRegistrationInfo(competitionId)                       => ???
        case GetCategories(competitionId)                             => ???
        case GetFightById(competitionId, categoryId, fightId)         => ???
        case GetCategory(competitionId, categoryId)                   => ???
        case GetFightsByMats(competitionId, categoryId, limit)     => ???
        case GetFightResulOptions(competitionId, categoryId, fightId) => ???
        case GetStagesForCategory(competitionId, categoryId)          => ???
        case GetStageById(competitionId, categoryId, stageId)         => ???
        case GetStageFights(competitionId, categoryId, stageId)       => ???
      }
    }
}
