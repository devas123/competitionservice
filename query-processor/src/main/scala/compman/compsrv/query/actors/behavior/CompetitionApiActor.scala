package compman.compsrv.query.actors.behavior

import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties._
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, ManagedCompetitionsOperations, Pagination}
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import io.getquill.CassandraZioSession
import zio.{Ref, RIO, Tag, ZIO}
import zio.logging.Logging

object CompetitionApiActor {

  case class Live(cassandraZioSession: CassandraZioSession) extends ActorContext {
    implicit val loggingLive: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .live(cassandraZioSession)
    implicit val managedCompetitionService: ManagedCompetitionService[LIO] = ManagedCompetitionsOperations
      .live(cassandraZioSession)
  }

  case class Test(
    competitions: Ref[Map[String, ManagedCompetition]],
    competitionProperties: Option[Map[String, CompetitionProperties]] = None,
    categories: Option[Map[String, Category]] = None,
    competitors: Option[Map[String, Competitor]] = None,
    fights: Option[Map[String, Fight]] = None,
    periods: Option[Map[String, Period]] = None,
    registrationPeriods: Option[Map[String, RegistrationPeriod]] = None,
    registrationGroups: Option[Map[String, RegistrationGroup]] = None,
    stages: Option[Map[String, StageDescriptor]] = None
  ) extends ActorContext {
    implicit val loggingLive: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations.test(
      competitionProperties,
      categories,
      competitors,
      fights,
      periods,
      registrationPeriods,
      registrationGroups,
      stages
    )
    implicit val managedCompetitionService: ManagedCompetitionService[LIO] = ManagedCompetitionsOperations
      .test(competitions)
  }

  trait ActorContext {
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO]
    implicit val managedCompetitionService: ManagedCompetitionService[LIO]
  }

  sealed trait ApiCommand[+_]

  final case object GetDefaultRestrictions extends ApiCommand[List[Restriction]]

  final case class GetAllCompetitions() extends ApiCommand[List[CompetitionProperties]]

  final case class GetCompetitionProperties(id: String) extends ApiCommand[Option[CompetitionProperties]]

  final case class GetCompetitionInfoTemplate(competitionId: String) extends ApiCommand[CompetitionInfoTemplate]

  final case class GetSchedule(competitionId: String) extends ApiCommand[List[Period]]

  final case class GetCompetitors(competitionId: String, searchString: Option[String], pagination: Option[Pagination])
      extends ApiCommand[List[Competitor]]

  final case class GetCompetitor(competitionId: String, competitorId: String) extends ApiCommand[List[Competitor]]

  final case class GetDashboard(competitionId: String) extends ApiCommand[List[Period]]

  final case class GetMats(competitionId: String) extends ApiCommand[List[Mat]]

  final case class GetMat(competitionId: String, matId: String) extends ApiCommand[Option[Mat]]

  final case class GetMatFights(competitionId: String, matId: String) extends ApiCommand[List[Fight]]

  final case class GetRegistrationInfo(competitionId: String) extends ApiCommand[Option[RegistrationInfo]]

  final case class GetCategories(competitionId: String) extends ApiCommand[List[Category]]

  final case class GetFightById(competitionId: String, categoryId: String, fightId: String)
      extends ApiCommand[Option[Fight]]

  final case class GetCategory(competitionId: String, categoryId: String) extends ApiCommand[Option[Category]]

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
      ): RIO[R with Logging, (ActorState, A)] = {
        for {
          _ <- Logging.info(s"Received API command $command")
          res <- command match {
            case GetDefaultRestrictions => ZIO.effect(DefaultRestrictions.restrictions.map(DtoMapping.mapRestriction))
                .map(r => (state, r.asInstanceOf[A]))
            case GetAllCompetitions() => ManagedCompetitionsOperations.getManagedCompetitions[LIO]
                .map(res => (state, res.asInstanceOf[A]))
            case GetCompetitionProperties(id) => CompetitionQueryOperations[LIO].getCompetitionProperties(id)
                .map(res => (state, res.asInstanceOf[A]))
            case GetCompetitionInfoTemplate(competitionId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetSchedule(competitionId) => CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                .map(res => (state, res.asInstanceOf[A]))
            case GetCompetitors(competitionId, searchString, pagination) => CompetitionQueryOperations[LIO]
                .getCompetitorsByCompetitionId(competitionId)(pagination, searchString)
                .map(res => (state, res.asInstanceOf[A]))
            case GetCompetitor(competitionId, competitorId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetDashboard(competitionId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetMats(competitionId) => CompetitionQueryOperations[LIO].getCompetitionInfoTemplate(competitionId)
                .map(res => (state, res.asInstanceOf[A]))
            case GetMat(competitionId, matId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetMatFights(competitionId, matId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetRegistrationInfo(competitionId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetCategories(competitionId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetFightById(competitionId, categoryId, fightId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetCategory(competitionId, categoryId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetFightsByMats(competitionId, categoryId, limit) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetFightResulOptions(competitionId, categoryId, fightId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetStagesForCategory(competitionId, categoryId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetStageById(competitionId, categoryId, stageId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetStageFights(competitionId, categoryId, stageId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(res => (state, res.asInstanceOf[A]))
          }
          _ <- Logging.info(s"Response: $res")
        } yield res
      }
    }
}
