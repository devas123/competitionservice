package compman.compsrv.logic.actors.behavior

import cats.data.OptionT
import compman.compsrv.Utils
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Behaviors}
import compman.compsrv.logic.category.CategoryGenerateService
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.model.commands.payload.AdjacencyList
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.PageResponse
import compman.compsrv.model.dto.brackets.{FightResultOptionDTO, StageDescriptorDTO}
import compman.compsrv.model.dto.dashboard.{MatDescriptionDTO, MatStateDTO}
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model._
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{
  CompetitionQueryOperations,
  FightQueryOperations,
  ManagedCompetitionsOperations,
  Pagination
}
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import org.mongodb.scala.MongoClient
import zio.{Ref, Tag, ZIO}
import zio.logging.Logging

import scala.jdk.CollectionConverters.IterableHasAsScala

object CompetitionApiActor {

  case class Live(mongoClient: MongoClient, mongodbConfig: MongodbConfig) extends ActorContext {
    implicit val loggingLive: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val fightQueryOperations: FightQueryOperations[LIO] = FightQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val managedCompetitionService: ManagedCompetitionService[LIO] = ManagedCompetitionsOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
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
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .test(competitionProperties, categories, competitors, periods, registrationPeriods, registrationGroups, stages)
    implicit val fightQueryOperations: FightQueryOperations[LIO] = FightQueryOperations.test(fights)
    implicit val managedCompetitionService: ManagedCompetitionService[LIO] = ManagedCompetitionsOperations
      .test(competitions)
  }

  trait ActorContext {
    implicit val loggingLive: compman.compsrv.logic.logging.CompetitionLogging.Service[LIO]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO]
    implicit val fightQueryOperations: FightQueryOperations[LIO]
    implicit val managedCompetitionService: ManagedCompetitionService[LIO]
  }

  sealed trait ApiCommand {
    type responseType
    val replyTo: ActorRef[responseType]
  }

  final case class GetDefaultRestrictions(override val replyTo: ActorRef[List[CategoryRestrictionDTO]])
      extends ApiCommand {
    override type responseType = List[CategoryRestrictionDTO]
  }
  final case class GetDefaultFightResults(override val replyTo: ActorRef[List[FightResultOptionDTO]])
      extends ApiCommand {
    override type responseType = List[FightResultOptionDTO]
  }
  final case class GetAllCompetitions(override val replyTo: ActorRef[List[ManagedCompetition]]) extends ApiCommand {
    override type responseType = List[ManagedCompetition]
  }

  final case class GenerateCategoriesFromRestrictions(
    restrictions: List[CategoryRestrictionDTO],
    idTrees: List[AdjacencyList],
    restrictionNames: List[String]
  )(override val replyTo: ActorRef[List[CategoryDescriptorDTO]])
      extends ApiCommand {
    override type responseType = List[CategoryDescriptorDTO]
  }

  final case class GetCompetitionProperties(id: String)(
    override val replyTo: ActorRef[Option[CompetitionPropertiesDTO]]
  ) extends ApiCommand {
    override type responseType = Option[CompetitionPropertiesDTO]
  }

  final case class GetCompetitionInfoTemplate(competitionId: String)(override val replyTo: ActorRef[String])
      extends ApiCommand {
    override type responseType = String
  }

  final case class GetSchedule(competitionId: String)(override val replyTo: ActorRef[ScheduleDTO]) extends ApiCommand {
    override type responseType = ScheduleDTO
  }

  final case class GetCompetitors(
    competitionId: String,
    categoryId: Option[String],
    searchString: Option[String],
    pagination: Option[Pagination]
  )(override val replyTo: ActorRef[PageResponse[CompetitorDTO]])
      extends ApiCommand {
    override type responseType = PageResponse[CompetitorDTO]
  }

  final case class GetCompetitor(competitionId: String, competitorId: String)(
    override val replyTo: ActorRef[Option[CompetitorDTO]]
  ) extends ApiCommand {
    override type responseType = Option[CompetitorDTO]
  }

  final case class GetDashboard(competitionId: String)(override val replyTo: ActorRef[List[Period]])
      extends ApiCommand {
    override type responseType = List[Period]
  }

  final case class GetMats(competitionId: String)(override val replyTo: ActorRef[List[MatDescriptionDTO]])
      extends ApiCommand {
    override type responseType = List[MatDescriptionDTO]
  }

  final case class GetPeriodMats(competitionId: String, periodId: String)(
    override val replyTo: ActorRef[MatsQueryResult]
  ) extends ApiCommand {
    override type responseType = MatsQueryResult
  }

  final case class GetMat(competitionId: String, matId: String)(
    override val replyTo: ActorRef[Option[MatDescriptionDTO]]
  ) extends ApiCommand {
    override type responseType = Option[MatDescriptionDTO]
  }

  final case class GetMatFights(competitionId: String, matId: String)(
    override val replyTo: ActorRef[MatFightsQueryResult]
  ) extends ApiCommand {
    override type responseType = MatFightsQueryResult
  }

  final case class GetRegistrationInfo(competitionId: String)(override val replyTo: ActorRef[Option[RegistrationInfoDTO]])
      extends ApiCommand {
    override type responseType = Option[RegistrationInfoDTO]
  }

  final case class GetCategories(competitionId: String)(override val replyTo: ActorRef[List[CategoryStateDTO]])
      extends ApiCommand {
    override type responseType = List[CategoryStateDTO]
  }

  final case class GetFightById(competitionId: String, categoryId: String, fightId: String)(
    override val replyTo: ActorRef[Option[FightDescriptionDTO]]
  ) extends ApiCommand {
    override type responseType = Option[FightDescriptionDTO]
  }
  final case class GetFightIdsByCategoryIds(competitionId: String)(
    override val replyTo: ActorRef[Option[Map[String, List[String]]]]
  ) extends ApiCommand {
    override type responseType = Option[Map[String, List[String]]]
  }

  final case class GetCategory(competitionId: String, categoryId: String)(
    override val replyTo: ActorRef[Option[CategoryStateDTO]]
  ) extends ApiCommand {
    override type responseType = Option[CategoryStateDTO]
  }

  final case class GetPeriodFightsByMats(competitionId: String, periodId: String, limit: Int)(
    override val replyTo: ActorRef[Map[String, List[String]]]
  ) extends ApiCommand {
    override type responseType = Map[String, List[String]]
  }

  final case class GetFightResulOptions(competitionId: String, categoryId: String, stageId: String)(
    override val replyTo: ActorRef[List[FightResultOptionDTO]]
  ) extends ApiCommand {
    override type responseType = List[FightResultOptionDTO]
  }

  final case class GetStagesForCategory(competitionId: String, categoryId: String)(
    override val replyTo: ActorRef[List[StageDescriptorDTO]]
  ) extends ApiCommand {
    override type responseType = List[StageDescriptorDTO]
  }
  final case class GetStageById(competitionId: String, categoryId: String, stageId: String)(
    override val replyTo: ActorRef[Option[StageDescriptorDTO]]
  ) extends ApiCommand {
    override type responseType = Option[StageDescriptorDTO]
  }
  final case class GetStageFights(competitionId: String, categoryId: String, stageId: String)(
    override val replyTo: ActorRef[List[FightDescriptionDTO]]
  ) extends ApiCommand {
    override type responseType = List[FightDescriptionDTO]
  }

  case class ActorState()
  val initialState: ActorState = ActorState()
  import Behaviors._
  def behavior[R: Tag](ctx: ActorContext): ActorBehavior[R with Logging, ActorState, ApiCommand] = Behaviors
    .behavior[R with Logging, ActorState, ApiCommand].withReceive { (_, _, state, command, _) =>
      {
        import cats.implicits._
        import ctx._
        import zio.interop.catz._
        for {
          _ <- Logging.info(s"Received API command $command")
          res <- command match {
            case c @ GenerateCategoriesFromRestrictions(restrictions, idTrees, restrictionNames) => for {
                restrictionNamesOrder <- ZIO.effect(restrictionNames.zipWithIndex.toMap)
                res <- idTrees.traverse(tree =>
                  ZIO.effect(
                    CategoryGenerateService
                      .generateCategoriesFromRestrictions(restrictions.toArray, tree, restrictionNamesOrder)
                  )
                )
                _ <- c.replyTo ! res.flatten
              } yield state
            case c: GetDefaultRestrictions => ZIO.effect(DefaultRestrictions.restrictions)
                .flatMap(res => c.replyTo ! res).as(state)
            case c: GetDefaultFightResults => ZIO.effect(FightResultOptionDTO.values.asScala)
                .flatMap(res => c.replyTo ! res.toList).as(state)
            case c: GetAllCompetitions => ManagedCompetitionsOperations.getActiveCompetitions[LIO]
                .flatMap(res => c.replyTo ! res).as(state)
            case c @ GetCompetitionProperties(id) => CompetitionQueryOperations[LIO].getCompetitionProperties(id)
                .map(_.map(DtoMapping.toDtoCompetitionProperties)).flatMap(res => c.replyTo ! res).as(state)
            case c @ GetCompetitionInfoTemplate(competitionId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(ci => ci.map(c => new String(c.template)).getOrElse(""))
                .flatMap(res => c.replyTo ! res).as(state)
            case c @ GetSchedule(competitionId) =>
              import extensions._
              for {
                periods                <- CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                fighsByScheduleEntries <- FightQueryOperations[LIO].getFightsByScheduleEntries(competitionId)
                mats = periods.flatMap(period => period.mats.map(DtoMapping.toDtoMat(period.id))).toArray
                dtoPeriods = periods.map(DtoMapping.toDtoPeriod)
                  .map(_.enrichWithFightsByScheduleEntries(fighsByScheduleEntries)).toArray
                _ <- c.replyTo ! new ScheduleDTO().setId(competitionId).setMats(mats).setPeriods(dtoPeriods)
              } yield state
            case c @ GetCompetitors(competitionId, categoryId, searchString, pagination) => categoryId match {
                case Some(value) => CompetitionQueryOperations[LIO]
                    .getCompetitorsByCategoryId(competitionId)(value, pagination, searchString)
                    .map(res => createPageResponse(competitionId, res)).flatMap(res => c.replyTo ! res).as(state)
                case None => CompetitionQueryOperations[LIO]
                    .getCompetitorsByCompetitionId(competitionId)(pagination, searchString)
                    .map(res => createPageResponse(competitionId, res)).flatMap(res => c.replyTo ! res).as(state)
              }
            case c @ GetCompetitor(competitionId, competitorId) => CompetitionQueryOperations[LIO]
                .getCompetitorById(competitionId)(competitorId)
                .flatMap(res => c.replyTo ! res.map(DtoMapping.toDtoCompetitor)).as(state)
            case c @ GetDashboard(competitionId) => CompetitionQueryOperations[LIO]
                .getPeriodsByCompetitionId(competitionId).flatMap(res => c.replyTo ! res).as(state)
            case c @ GetMats(competitionId) => CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                .map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id)))).flatMap(res => c.replyTo ! res).as(state)
            case c @ GetMat(competitionId, matId) => CompetitionQueryOperations[LIO]
                .getPeriodsByCompetitionId(competitionId)
                .map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id))).find(_.getId == matId))
                .flatMap(res => c.replyTo ! res).as(state)

            case c @ GetMatFights(competitionId, matId) => for {
                fights <- FightQueryOperations[LIO].getFightsByMat(competitionId)(matId, 20)
                fightDtos = fights.map(DtoMapping.toDtoFight)
                competitors = fights.flatMap(_.scores).filter(_.competitorId.isDefined).map(cs =>
                  CompetitorDisplayInfo(
                    cs.competitorId.orNull,
                    cs.competitorFirstName,
                    cs.competitorLastName,
                    cs.competitorAcademyName
                  )
                ).map(DtoMapping.toDtoCompetitor)
                _ <- c.replyTo ! MatFightsQueryResult(competitors, fightDtos)
              } yield state
            case c @ GetRegistrationInfo(competitionId) => for {
              properties <- CompetitionQueryOperations[LIO].getCompetitionProperties(competitionId)
                groups  <- CompetitionQueryOperations[LIO].getRegistrationGroups(competitionId)
                periods <- CompetitionQueryOperations[LIO].getRegistrationPeriods(competitionId)
                _ <- c.replyTo ! Option(RegistrationInfo(Utils.groupById(groups)(_.id), Utils.groupById(periods)(_.id))).map(DtoMapping.toDtoRegistrationInfo(properties.exists(_.registrationOpen), competitionId))
              } yield state
            case c @ GetCategories(competitionId) => for {
                categories <- CompetitionQueryOperations[LIO].getCategoriesByCompetitionId(competitionId)
                categoryStates <- categories.traverse { category =>
                  for {
                    numberOfFights <- FightQueryOperations[LIO].getNumberOfFightsForCategory(competitionId)(category.id)
                    numberOfCompetitors <- CompetitionQueryOperations[LIO]
                      .getNumberOfCompetitorsForCategory(competitionId)(category.id)
                  } yield createCategoryState(competitionId, category, numberOfFights, numberOfCompetitors)
                }
                _ <- c.replyTo ! categoryStates
              } yield state
            case c @ GetFightById(competitionId, categoryId, fightId) => FightQueryOperations[LIO]
                .getFightById(competitionId)(categoryId, fightId).map(_.map(DtoMapping.toDtoFight))
                .flatMap(res => c.replyTo ! res).as(state)
            case c @ GetFightIdsByCategoryIds(competitionId) => FightQueryOperations[LIO]
                .getFightIdsByCategoryIds(competitionId).flatMap(res => c.replyTo ! Option(res)).as(state)
            case c @ GetCategory(competitionId, categoryId) => for {
                res <- (for {
                  category <- OptionT(CompetitionQueryOperations[LIO].getCategoryById(competitionId)(categoryId))
                  numberOfFights <- OptionT
                    .liftF(FightQueryOperations[LIO].getNumberOfFightsForCategory(competitionId)(category.id))
                  numberOfCompetitors <- OptionT.liftF(
                    CompetitionQueryOperations[LIO].getNumberOfCompetitorsForCategory(competitionId)(category.id)
                  )
                } yield createCategoryState(competitionId, category, numberOfFights, numberOfCompetitors)).value
                _ <- c.replyTo ! res
              } yield state

            case c @ GetPeriodMats(competitionId, periodId) =>
              val optionRes = for {
                period <- OptionT(CompetitionQueryOperations[LIO].getPeriodById(competitionId)(periodId))
                mats = period.mats
                res <- mats.traverse(mat =>
                  for {
                    fights <- OptionT.liftF(FightQueryOperations[LIO].getFightsByMat(competitionId)(mat.matId, 10))
                    numberOfFights <- OptionT
                      .liftF(FightQueryOperations[LIO].getNumberOfFightsForMat(competitionId)(mat.matId))
                    competitors = fights.flatMap(_.scores).filter(_.competitorId.isDefined).map(cs =>
                      CompetitorDisplayInfo(
                        cs.competitorId.get,
                        cs.competitorFirstName,
                        cs.competitorLastName,
                        cs.competitorAcademyName
                      )
                    ).map(DtoMapping.toDtoCompetitor)
                    matState = new MatStateDTO().setMatDescription(DtoMapping.toDtoMat(period.id)(mat))
                      .setTopFiveFights(fights.map(DtoMapping.toDtoFight).toArray).setNumberOfFights(numberOfFights)
                  } yield (matState, competitors)
                )
              } yield MatsQueryResult(res.flatMap(_._2), res.map(_._1))
              optionRes.value.flatMap(res => c.replyTo ! res.getOrElse(MatsQueryResult(List.empty, List.empty)))
                .as(state)
            case c @ GetPeriodFightsByMats(competitionId, periodId, limit) =>
              import cats.implicits._
              import zio.interop.catz._
              for {
                period <- CompetitionQueryOperations[LIO].getPeriodById(competitionId)(periodId)
                mats = period.map(_.mats).getOrElse(List.empty).map(_.matId)
                fights <- mats
                  .traverse(mat => FightQueryOperations[LIO].getFightsByMat(competitionId)(mat, limit).map(mat -> _))
                _ <- c.replyTo ! fights.map(entry => (entry._1, entry._2.map(_.id))).toMap
              } yield state
            case c @ GetFightResulOptions(competitionId, categoryId, stageId) => for {
                stage <- CompetitionQueryOperations[LIO].getStageById(competitionId)(categoryId, stageId)
                fightResultOptions = stage.flatMap(_.stageResultDescriptor)
                  .map(_.fightResultOptions.map(DtoMapping.toDtoFightResultOption)).getOrElse(List.empty)
                _ <- c.replyTo ! fightResultOptions
              } yield state
            case c @ GetStagesForCategory(competitionId, categoryId) => CompetitionQueryOperations[LIO]
                .getStagesByCategory(competitionId)(categoryId)
                .flatMap(res => c.replyTo ! res.map(DtoMapping.toDtoStageDescriptor)).as(state)
            case c @ GetStageById(competitionId, categoryId, stageId) => CompetitionQueryOperations[LIO]
                .getStageById(competitionId)(categoryId, stageId)
                .flatMap(res => c.replyTo ! res.map(DtoMapping.toDtoStageDescriptor)).as(state)
            case c @ GetStageFights(competitionId, categoryId, stageId) => FightQueryOperations[LIO]
                .getFightsByStage(competitionId)(categoryId, stageId).map(_.map(DtoMapping.toDtoFight))
                .flatMap(res => c.replyTo ! res).as(state)
          }
        } yield res
      }
    }

  private def createPageResponse(competitionId: String, res: (List[Competitor], Pagination)) = {
    new PageResponse[CompetitorDTO](
      competitionId,
      res._2.totalResults.toLong,
      Integer.signum(Integer.bitCount(res._2.maxResults)) * res._2.offset / Math.max(res._2.maxResults, 1),
      res._1.map(DtoMapping.toDtoCompetitor).toArray
    )
  }

  private def createCategoryState(
    competitionId: String,
    category: Category,
    numberOfFights: Int,
    numberOfCompetitors: Int
  ) = {
    new CategoryStateDTO().setCategory(DtoMapping.toDtoCategory(category)).setId(category.id)
      .setCompetitionId(competitionId).setNumberOfCompetitors(numberOfCompetitors).setFightsNumber(numberOfFights)
  }
}
