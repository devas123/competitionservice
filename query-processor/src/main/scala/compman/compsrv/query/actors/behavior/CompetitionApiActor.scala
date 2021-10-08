package compman.compsrv.query.actors.behavior

import compman.compsrv.logic.category.CategoryGenerateService
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.commands.payload.AdjacencyList
import compman.compsrv.model.dto.competition.CategoryRestrictionDTO
import compman.compsrv.query.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.query.actors.ActorSystem.ActorConfig
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties._
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, ManagedCompetitionsOperations, Pagination}
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import io.getquill.{CassandraContextConfig, CassandraZioSession}
import zio.{Ref, RIO, Tag, ZIO}
import zio.logging.Logging

object CompetitionApiActor {

  case class Live(cassandraConfig: CassandraContextConfig) extends ActorContext {
    implicit val loggingLive: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]
    private val cassandraZioSession =
      CassandraZioSession(cassandraConfig.cluster, cassandraConfig.keyspace, cassandraConfig.preparedStatementCacheSize)
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .live(cassandraZioSession)
    implicit val managedCompetitionService: ManagedCompetitionService[LIO] = ManagedCompetitionsOperations
      .live(cassandraZioSession)
  }

  case class Test(
    competitions: Ref[Map[String, ManagedCompetition]],
    competitionProperties: Option[Ref[Map[String, CompetitionProperties]]] = None,
    categories: Option[Ref[Map[String, Category]]] = None,
    competitors: Option[Ref[Map[String, Competitor]]] = None,
    fights: Option[Ref[Map[String, Fight]]] = None,
    periods: Option[Ref[Map[String, Period]]] = None,
    registrationPeriods: Option[Ref[Map[String, RegistrationPeriod]]] = None,
    registrationGroups: Option[Ref[Map[String, RegistrationGroup]]] = None,
    stages: Option[Ref[Map[String, StageDescriptor]]] = None
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

  final case object GetAllCompetitions extends ApiCommand[List[CompetitionProperties]]

  final case class GenerateCategoriesFromRestrictions(
    restrictions: List[CategoryRestrictionDTO],
    idTrees: List[AdjacencyList],
    restrictionNames: List[String]
  )                                                     extends ApiCommand[List[Category]]
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

  final case class GetFightById(competitionId: String, fightId: String) extends ApiCommand[Option[Fight]]

  final case class GetCategory(competitionId: String, categoryId: String) extends ApiCommand[Option[Category]]

  final case class GetFightsByMats(competitionId: String, periodId: String, limit: Int)
      extends ApiCommand[Map[String, List[String]]]

  final case class GetFightResulOptions(competitionId: String, stageId: String) extends ApiCommand[List[FightResult]]

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
        import cats.implicits._
        import zio.interop.catz._
        for {
          _ <- Logging.info(s"Received API command $command")
          res <- command match {
            case GenerateCategoriesFromRestrictions(restrictions, idTrees, restrictionNames) =>
              for {
                restrictionNamesOrder <- ZIO.effect(restrictionNames.zipWithIndex.toMap)
                res <- idTrees.traverse(tree => ZIO.effect(CategoryGenerateService.generateCategoriesFromRestrictions(restrictions.toArray, tree, restrictionNamesOrder)))
              } yield (state, res.flatten.asInstanceOf[A])
            case GetDefaultRestrictions => ZIO.effect(DefaultRestrictions.restrictions.map(DtoMapping.mapRestriction))
                .map(r => (state, r.asInstanceOf[A]))
            case GetAllCompetitions => ManagedCompetitionsOperations.getManagedCompetitions[LIO]
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
                .getCompetitorById(competitionId)(competitorId).map(res => (state, res.asInstanceOf[A]))
            case GetDashboard(competitionId) => CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                .map(res => (state, res.asInstanceOf[A]))
            case GetMats(competitionId) => CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                .map(_.flatMap(_.mats)).map(res => (state, res.asInstanceOf[A]))
            case GetMat(competitionId, matId) => CompetitionQueryOperations[LIO]
                .getPeriodsByCompetitionId(competitionId).map(_.flatMap(_.mats).find(_.matId == matId))
                .map(res => (state, res.asInstanceOf[A]))

            case GetMatFights(competitionId, matId) => CompetitionQueryOperations[LIO]
                .getFightsByMat(competitionId)(matId, 20).map(res => (state, res.asInstanceOf[A]))
            case GetRegistrationInfo(competitionId) => for {
                groups  <- CompetitionQueryOperations[LIO].getRegistrationGroups(competitionId)
                periods <- CompetitionQueryOperations[LIO].getRegistrationPeriods(competitionId)
              } yield (state, RegistrationInfo(groups, periods).asInstanceOf[A])
            case GetCategories(competitionId) => CompetitionQueryOperations[LIO]
                .getCategoriesByCompetitionId(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetFightById(competitionId, fightId) => CompetitionQueryOperations[LIO]
                .getFightById(competitionId)(fightId).map(res => (state, res.asInstanceOf[A]))
            case GetCategory(competitionId, categoryId) => CompetitionQueryOperations[LIO]
                .getCategoryById(competitionId)(categoryId).map(res => (state, res.asInstanceOf[A]))
            case GetFightsByMats(competitionId, periodId, limit) =>
              import cats.implicits._
              import zio.interop.catz._
              for {
                period <- CompetitionQueryOperations[LIO].getPeriodById(competitionId)(periodId)
                mats = period.map(_.mats).getOrElse(List.empty).map(_.matId)
                fights <- mats.traverse(mat =>
                  CompetitionQueryOperations[LIO].getFightsByMat(competitionId)(mat, limit).map(mat -> _)
                )
              } yield (state, fights.asInstanceOf[A])
            case GetFightResulOptions(competitionId, stageId) => for {
                stage <- CompetitionQueryOperations[LIO].getStageById(competitionId)(stageId)
                fightResultOptions = stage.flatMap(_.stageResultDescriptor).map(_.fightResultOptions)
                  .getOrElse(List.empty)
              } yield (state, fightResultOptions.asInstanceOf[A])
            case GetStagesForCategory(competitionId, categoryId) => CompetitionQueryOperations[LIO]
                .getStagesByCategory(competitionId)(categoryId).map(res => (state, res.asInstanceOf[A]))
            case GetStageById(competitionId, _, stageId) => CompetitionQueryOperations[LIO]
                .getStageById(competitionId)(stageId).map(res => (state, res.asInstanceOf[A]))
            case GetStageFights(competitionId, _, stageId) => CompetitionQueryOperations[LIO]
                .getFightsByStage(competitionId)(stageId).map(res => (state, res.asInstanceOf[A]))
          }
          _ <- Logging.info(s"Response: $res")
        } yield res
      }
    }
}
