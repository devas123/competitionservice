package compman.compsrv.logic.actors.behavior

import cats.data.OptionT
import compman.compsrv.logic.actors.{ActorBehavior, Context, Timers}
import compman.compsrv.logic.actors.ActorSystem.ActorConfig
import compman.compsrv.logic.category.CategoryGenerateService
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.commands.payload.AdjacencyList
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.PageResponse
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.dashboard.{MatDescriptionDTO, MatStateDTO}
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.query.model._
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, ManagedCompetitionsOperations, Pagination}
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import io.getquill.{CassandraContextConfig, CassandraZioSession}
import zio.{Ref, RIO, Tag, ZIO}
import zio.logging.Logging

import scala.jdk.CollectionConverters.IterableHasAsScala

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

  final case object GetDefaultRestrictions extends ApiCommand[List[CategoryRestrictionDTO]]
  final case object GetDefaultFightResults extends ApiCommand[List[FightResultOptionDTO]]

  final case object GetAllCompetitions extends ApiCommand[List[ManagedCompetition]]

  final case class GenerateCategoriesFromRestrictions(
    restrictions: List[CategoryRestrictionDTO],
    idTrees: List[AdjacencyList],
    restrictionNames: List[String]
  )                                                     extends ApiCommand[List[CategoryDescriptorDTO]]
  final case class GetCompetitionProperties(id: String) extends ApiCommand[Option[CompetitionPropertiesDTO]]

  final case class GetCompetitionInfoTemplate(competitionId: String) extends ApiCommand[String]

  final case class GetSchedule(competitionId: String) extends ApiCommand[ScheduleDTO]

  final case class GetCompetitors(
    competitionId: String,
    categoryId: Option[String],
    searchString: Option[String],
    pagination: Option[Pagination]
  ) extends ApiCommand[PageResponse[CompetitorDTO]]

  final case class GetCompetitor(competitionId: String, competitorId: String) extends ApiCommand[List[CompetitorDTO]]

  final case class GetDashboard(competitionId: String) extends ApiCommand[List[Period]]

  final case class GetMats(competitionId: String) extends ApiCommand[List[MatDescriptionDTO]]

  final case class GetPeriodMats(competitionId: String, periodId: String) extends ApiCommand[MatsQueryResult]

  final case class GetMat(competitionId: String, matId: String) extends ApiCommand[Option[MatDescriptionDTO]]

  final case class GetMatFights(competitionId: String, matId: String) extends ApiCommand[MatFightsQueryResult]

  final case class GetRegistrationInfo(competitionId: String) extends ApiCommand[Option[RegistrationInfo]]

  final case class GetCategories(competitionId: String) extends ApiCommand[List[CategoryStateDTO]]

  final case class GetFightById(competitionId: String, categoryId: String, fightId: String)
      extends ApiCommand[Option[FightDescriptionDTO]]
  final case class GetFightIdsByCategoryIds(competitionId: String) extends ApiCommand[Option[Map[String, List[String]]]]

  final case class GetCategory(competitionId: String, categoryId: String) extends ApiCommand[Option[CategoryStateDTO]]

  final case class GetPeriodFightsByMats(competitionId: String, periodId: String, limit: Int)
      extends ApiCommand[Map[String, List[String]]]

  final case class GetFightResulOptions(competitionId: String, stageId: String) extends ApiCommand[List[FightResult]]

  final case class GetStagesForCategory(competitionId: String, categoryId: String)
      extends ApiCommand[List[StageDescriptor]]
  final case class GetStageById(competitionId: String, categoryId: String, stageId: String)
      extends ApiCommand[List[StageDescriptor]]
  final case class GetStageFights(competitionId: String, categoryId: String, stageId: String)
      extends ApiCommand[List[FightDescriptionDTO]]

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
            case GenerateCategoriesFromRestrictions(restrictions, idTrees, restrictionNames) => for {
                restrictionNamesOrder <- ZIO.effect(restrictionNames.zipWithIndex.toMap)
                res <- idTrees.traverse(tree =>
                  ZIO.effect(
                    CategoryGenerateService
                      .generateCategoriesFromRestrictions(restrictions.toArray, tree, restrictionNamesOrder)
                  )
                )
              } yield (state, res.flatten.asInstanceOf[A])
            case GetDefaultRestrictions => ZIO.effect(DefaultRestrictions.restrictions)
                .map(r => (state, r.asInstanceOf[A]))
            case GetDefaultFightResults => ZIO.effect(FightResultOptionDTO.values.asScala)
                .map(r => (state, r.asInstanceOf[A]))
            case GetAllCompetitions => ManagedCompetitionsOperations.getManagedCompetitions[LIO]
                .map(res => (state, res.asInstanceOf[A]))
            case GetCompetitionProperties(id) => CompetitionQueryOperations[LIO].getCompetitionProperties(id)
                .map(_.map(DtoMapping.toDtoCompetitionProperties)).map(res => (state, res.asInstanceOf[A]))
            case GetCompetitionInfoTemplate(competitionId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(ci => ci.map(c => new String(c.template)).getOrElse(""))
                .map(res => (state, res.asInstanceOf[A]))
            case GetSchedule(competitionId) =>
              import extensions._
              for {
                periods <- CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                fighsByScheduleEntries <- CompetitionQueryOperations[LIO].getFightsByScheduleEntries(competitionId)
                mats = periods.flatMap(period => period.mats.map(DtoMapping.toDtoMat(period.id))).toArray
                dtoPeriods = periods.map(DtoMapping.toDtoPeriod).map(_.enrichWithFightsByScheduleEntries(fighsByScheduleEntries)).toArray
              } yield (state, new ScheduleDTO().setId(competitionId).setMats(mats).setPeriods(dtoPeriods).asInstanceOf[A])
            case GetCompetitors(competitionId, categoryId, searchString, pagination) => categoryId match {
                case Some(value) => CompetitionQueryOperations[LIO]
                    .getCompetitorsByCategoryId(competitionId)(value, pagination, searchString)
                    .map(res => createPageResponse(competitionId, res)).map(res => (state, res.asInstanceOf[A]))
                case None => CompetitionQueryOperations[LIO]
                    .getCompetitorsByCompetitionId(competitionId)(pagination, searchString)
                    .map(res => createPageResponse(competitionId, res)).map(res => (state, res.asInstanceOf[A]))
              }
            case GetCompetitor(competitionId, competitorId) => CompetitionQueryOperations[LIO]
                .getCompetitorById(competitionId)(competitorId).map(res => (state, res.asInstanceOf[A]))
            case GetDashboard(competitionId) => CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                .map(res => (state, res.asInstanceOf[A]))
            case GetMats(competitionId) => CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                .map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id)))).map(res => (state, res.asInstanceOf[A]))
            case GetMat(competitionId, matId) => CompetitionQueryOperations[LIO]
                .getPeriodsByCompetitionId(competitionId).map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id))).find(_.getId == matId))
                .map(res => (state, res.asInstanceOf[A]))

            case GetMatFights(competitionId, matId) => for {
              fights <- CompetitionQueryOperations[LIO]
              .getFightsByMat(competitionId)(matId, 20)
              fightDtos = fights.map(DtoMapping.toDtoFight)
              competitors = fights.flatMap(_.scores).filter(_.competitorId.isDefined).map(cs => CompetitorDisplayInfo(cs.competitorId.orNull, cs.competitorFirstName, cs.competitorLastName, cs.competitorAcademyName))
                .map(DtoMapping.toDtoCompetitor)
            } yield (state, MatFightsQueryResult(competitors, fightDtos).asInstanceOf[A])
            case GetRegistrationInfo(competitionId) => for {
                groups  <- CompetitionQueryOperations[LIO].getRegistrationGroups(competitionId)
                periods <- CompetitionQueryOperations[LIO].getRegistrationPeriods(competitionId)
              } yield (state, RegistrationInfo(groups, periods).asInstanceOf[A])
            case GetCategories(competitionId) => for {
                categories <- CompetitionQueryOperations[LIO].getCategoriesByCompetitionId(competitionId)
                categoryStates <- categories.traverse { category =>
                  for {
                    numberOfFights <- CompetitionQueryOperations[LIO]
                      .getNumberOfFightsForCategory(competitionId)(category.id)
                    numberOfCompetitors <- CompetitionQueryOperations[LIO]
                      .getNumberOfCompetitorsForCategory(competitionId)(category.id)
                  } yield createCategoryState(competitionId, category, numberOfFights, numberOfCompetitors)
                }
              } yield (state, categoryStates.asInstanceOf[A])
            case GetFightById(competitionId, categoryId, fightId) => CompetitionQueryOperations[LIO]
                .getFightById(competitionId)(categoryId, fightId).map(_.map(DtoMapping.toDtoFight))
                .map(res => (state, res.asInstanceOf[A]))
            case GetFightIdsByCategoryIds(competitionId) => CompetitionQueryOperations[LIO]
                .getFightIdsByCategoryIds(competitionId).map(res => (state, res.asInstanceOf[A]))
            case GetCategory(competitionId, categoryId) => for {
                res <- (for {
                  category <- OptionT(CompetitionQueryOperations[LIO].getCategoryById(competitionId)(categoryId))
                  numberOfFights <- OptionT
                    .liftF(CompetitionQueryOperations[LIO].getNumberOfFightsForCategory(competitionId)(category.id))
                  numberOfCompetitors <- OptionT.liftF(
                    CompetitionQueryOperations[LIO].getNumberOfCompetitorsForCategory(competitionId)(category.id)
                  )
                } yield (
                  state,
                  createCategoryState(competitionId, category, numberOfFights, numberOfCompetitors).asInstanceOf[A]
                )).value
              } yield res.getOrElse((state, List.empty.asInstanceOf[A]))

            case GetPeriodMats(competitionId, periodId) =>
              val optionRes = for {
                period <- OptionT(CompetitionQueryOperations[LIO]
                  .getPeriodById(competitionId)(periodId)
                )
                mats = period.mats
                res <- mats.traverse(mat => for {
                  fights <- OptionT.liftF(CompetitionQueryOperations[LIO].getFightsByMat(competitionId)(mat.matId, 10))
                  numberOfFights <- OptionT.liftF(CompetitionQueryOperations[LIO].getNumberOfFightsForMat(competitionId)(mat.matId))
                  competitors = fights.flatMap(_.scores).filter(_.competitorId.isDefined)
                    .map(cs => CompetitorDisplayInfo(cs.competitorId.get, cs.competitorFirstName, cs.competitorLastName, cs.competitorAcademyName))
                    .map(DtoMapping.toDtoCompetitor)
                  matState = new MatStateDTO()
                    .setMatDescription(DtoMapping.toDtoMat(period.id)(mat))
                    .setTopFiveFights(fights.map(DtoMapping.toDtoFight).toArray)
                    .setNumberOfFights(
                      numberOfFights)
                } yield (matState, competitors))
              } yield MatsQueryResult(res.flatMap(_._2), res.map(_._1))
              optionRes.value.map(_.getOrElse(List.empty)).map(mats => (state, mats.asInstanceOf[A]))
            case GetPeriodFightsByMats(competitionId, periodId, limit) =>
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
            case GetStageFights(competitionId, categoryId, stageId) => CompetitionQueryOperations[LIO]
                .getFightsByStage(competitionId)(categoryId, stageId).map(_.map(DtoMapping.toDtoFight))
                .map(res => (state, res.asInstanceOf[A]))
          }
          _ <- Logging.info(s"Response: $res")
        } yield res
      }
    }

  private def createPageResponse[R: Tag, A](competitionId: String, res: (List[Competitor], Pagination)) = {
    new PageResponse[CompetitorDTO](
      competitionId,
      res._2.totalResults,
      Integer.signum(Integer.bitCount(res._2.maxResults)) * res._2.offset / Math.max(res._2.maxResults, 1),
      res._1.map(DtoMapping.toDtoCompetitor).toArray
    )
  }

  private def createCategoryState[R: Tag, A](
    competitionId: String,
    category: Category,
    numberOfFights: Int,
    numberOfCompetitors: Int
  ) = {
    new CategoryStateDTO().setCategory(DtoMapping.toDtoCategory(category)).setId(category.id)
      .setCompetitionId(competitionId).setNumberOfCompetitors(numberOfCompetitors).setFightsNumber(numberOfFights)
  }
}
