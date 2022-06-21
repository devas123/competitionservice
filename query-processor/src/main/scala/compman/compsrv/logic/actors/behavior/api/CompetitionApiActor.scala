package compman.compsrv.logic.actors.behavior.api

import cats.data.OptionT
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.logic.actors.{ActorBehavior, ActorRef, Behaviors}
import compman.compsrv.logic.category.CategoryGenerateService
import compman.compsrv.logic.fight.FightResultOptionConstants
import compman.compsrv.logic.logging.CompetitionLogging
import compman.compsrv.logic.logging.CompetitionLogging.LIO
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model._
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, FightQueryOperations, ManagedCompetitionsOperations, Pagination}
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import compservice.model.protobuf.model
import compservice.model.protobuf.query.{MatFightsQueryResult, MatsQueryResult, _}
import org.mongodb.scala.MongoClient
import zio.{Ref, Tag, ZIO}
import zio.logging.Logging

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
    registrationInfo: Option[Ref[Map[String, RegistrationInfo]]] = None,
    stages: Option[Ref[Map[String, StageDescriptor]]] = None
  ) extends ActorContext {
    implicit val loggingLive: CompetitionLogging.Service[LIO] = CompetitionLogging.Live.live[Any]
    implicit val competitionQueryOperations: CompetitionQueryOperations[LIO] = CompetitionQueryOperations
      .test(competitionProperties, registrationInfo, categories, competitors, periods, stages)
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

  sealed trait CompetitionApiCommand {
    type responseType = QueryServiceResponse
    val replyTo: ActorRef[responseType]
  }

  final case class GetDefaultRestrictions(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}
  final case class GetDefaultFightResults(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}
  final case class GetAllCompetitions(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GenerateCategoriesFromRestrictions(
    restrictions: List[model.CategoryRestriction],
    idTrees: List[model.AdjacencyList],
    restrictionNames: List[String]
  )(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetCompetitionProperties(id: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetCompetitionInfoTemplate(competitionId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}

  final case class GetSchedule(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetCompetitors(
    competitionId: String,
    categoryId: Option[String],
    searchString: Option[String],
    pagination: Option[Pagination]
  )(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetCompetitor(competitionId: String, competitorId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}

  final case class GetDashboard(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetMats(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetPeriodMats(competitionId: String, periodId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}

  final case class GetMat(competitionId: String, matId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetMatFights(competitionId: String, matId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}

  final case class GetRegistrationInfo(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetCategories(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetFightById(competitionId: String, categoryId: String, fightId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}
  final case class GetFightIdsByCategoryIds(competitionId: String)(override val replyTo: ActorRef[QueryServiceResponse])
      extends CompetitionApiCommand {}

  final case class GetCategory(competitionId: String, categoryId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}

  final case class GetPeriodFightsByMats(competitionId: String, periodId: String, limit: Int)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}

  final case class GetFightResulOptions(competitionId: String, categoryId: String, stageId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}

  final case class GetStagesForCategory(competitionId: String, categoryId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}
  final case class GetStageById(competitionId: String, categoryId: String, stageId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}
  final case class GetStageFights(competitionId: String, categoryId: String, stageId: String)(
    override val replyTo: ActorRef[QueryServiceResponse]
  ) extends CompetitionApiCommand {}

  case class ActorState()
  val initialState: ActorState = ActorState()
  import Behaviors._
  def behavior[R: Tag](ctx: ActorContext): ActorBehavior[R with Logging, ActorState, CompetitionApiCommand] = Behaviors
    .behavior[R with Logging, ActorState, CompetitionApiCommand].withReceive { (_, _, state, command, _) =>
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
                _ <- c.replyTo ! QueryServiceResponse().withGenerateCategoriesFromRestrictionsResponse(
                  GenerateCategoriesFromRestrictionsResponse(res.flatten)
                )
              } yield state
            case c: GetDefaultRestrictions => ZIO.effect(DefaultRestrictions.restrictions).flatMap(res =>
                c.replyTo ! QueryServiceResponse()
                  .withGetDefaultRestrictionsResponse(GetDefaultRestrictionsResponse(res))
              ).as(state)
            case c: GetDefaultFightResults => ZIO.effect(FightResultOptionConstants.values).flatMap(res =>
                c.replyTo ! QueryServiceResponse()
                  .withGetDefaultFightResultsResponse(GetDefaultFightResultsResponse(res))
              ).as(state)
            case c: GetAllCompetitions => ManagedCompetitionsOperations.getActiveCompetitions[LIO].flatMap(res =>
                c.replyTo ! QueryServiceResponse().withGetAllCompetitionsResponse(GetAllCompetitionsResponse(
                  res.map(DtoMapping.toDtoManagedCompetition)
                ))
              ).as(state)
            case c @ GetCompetitionProperties(id) => CompetitionQueryOperations[LIO].getCompetitionProperties(id)
                .map(_.map(DtoMapping.toDtoCompetitionProperties)).flatMap(res =>
                  c.replyTo ! QueryServiceResponse()
                    .withGetCompetitionPropertiesResponse(GetCompetitionPropertiesResponse(res))
                ).as(state)
            case c @ GetCompetitionInfoTemplate(competitionId) => CompetitionQueryOperations[LIO]
                .getCompetitionInfoTemplate(competitionId).map(ci => ci.map(c => new String(c.template)).getOrElse(""))
                .flatMap(res =>
                  c.replyTo ! QueryServiceResponse()
                    .withGetCompetitionInfoTemplateResponse(GetCompetitionInfoTemplateResponse(Option(res)))
                ).as(state)
            case c @ GetSchedule(competitionId) =>
              import extensions._
              for {
                periods                <- CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                fighsByScheduleEntries <- FightQueryOperations[LIO].getFightsByScheduleEntries(competitionId)
                mats = periods.flatMap(period => period.mats.map(DtoMapping.toDtoMat(period.id)))
                dtoPeriods = periods.map(DtoMapping.toDtoPeriod)
                  .map(_.enrichWithFightsByScheduleEntries(fighsByScheduleEntries))
                _ <- c.replyTo ! QueryServiceResponse().withGetScheduleResponse(GetScheduleResponse(
                  Some(model.Schedule().withId(competitionId).withMats(mats).withPeriods(dtoPeriods))
                ))
              } yield state
            case c @ GetCompetitors(competitionId, categoryId, searchString, pagination) => categoryId match {
                case Some(value) => CompetitionQueryOperations[LIO]
                    .getCompetitorsByCategoryId(competitionId)(value, pagination, searchString)
                    .flatMap(res => c.replyTo ! createGetCompetitorsResponse(res)).as(state)
                case None => CompetitionQueryOperations[LIO]
                    .getCompetitorsByCompetitionId(competitionId)(pagination, searchString)
                    .flatMap(res => c.replyTo ! createGetCompetitorsResponse(res)).as(state)
              }
            case c @ GetCompetitor(competitionId, competitorId) => CompetitionQueryOperations[LIO]
                .getCompetitorById(competitionId)(competitorId).flatMap(res =>
                  c.replyTo ! QueryServiceResponse()
                    .withGetCompetitorResponse(GetCompetitorResponse(res.map(DtoMapping.toDtoCompetitor)))
                ).as(state)
            case c @ GetDashboard(competitionId) => CompetitionQueryOperations[LIO]
                .getPeriodsByCompetitionId(competitionId).flatMap(res =>
                  c.replyTo ! QueryServiceResponse()
                    .withGetDashboardResponse(GetDashboardResponse(res.map(DtoMapping.toDtoPeriod)))
                ).as(state)
            case c @ GetMats(competitionId) => CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                .map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id))))
                .flatMap(res => c.replyTo ! QueryServiceResponse().withGetMatsResponse(GetMatsResponse(res))).as(state)
            case c @ GetMat(competitionId, matId) => CompetitionQueryOperations[LIO]
                .getPeriodsByCompetitionId(competitionId)
                .map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id))).find(_.id == matId))
                .flatMap(res => c.replyTo ! QueryServiceResponse().withGetMatResponse(GetMatResponse(res))).as(state)
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
                _ <- c.replyTo ! QueryServiceResponse()
                  .withGetMatFightsResponse(GetMatFightsResponse(Some(MatFightsQueryResult(competitors, fightDtos))))
              } yield state
            case c @ GetRegistrationInfo(competitionId) => for {
                regInfo <- CompetitionQueryOperations[LIO].getRegistrationInfo(competitionId)
                _ <- c.replyTo ! QueryServiceResponse().withGetRegistrationInfoResponse(GetRegistrationInfoResponse(
                  regInfo.map(DtoMapping.toDtoRegistrationInfo)
                ))
              } yield state
            case c @ GetCategories(competitionId) => for {
                categories <- CompetitionQueryOperations[LIO].getCategoriesByCompetitionId(competitionId)
                periods    <- CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId)
                categoryStartTimes = createCategoryIdToStartTimeMap(periods)
                categoryStates <- categories.traverse { category =>
                  for {
                    numberOfFights <- FightQueryOperations[LIO].getNumberOfFightsForCategory(competitionId)(category.id)
                    numberOfCompetitors <- CompetitionQueryOperations[LIO]
                      .getNumberOfCompetitorsForCategory(competitionId)(category.id)
                  } yield createCategoryState(
                    competitionId,
                    category,
                    numberOfFights,
                    numberOfCompetitors,
                    categoryStartTimes.get(category.id).map(Timestamps.fromDate).map(Timestamp.fromJavaProto)
                  )
                }
                _ <- c.replyTo ! QueryServiceResponse().withGetCategoriesResponse(GetCategoriesResponse(categoryStates))
              } yield state
            case c @ GetFightById(competitionId, categoryId, fightId) => FightQueryOperations[LIO]
                .getFightById(competitionId)(categoryId, fightId).map(_.map(DtoMapping.toDtoFight))
                .flatMap(res => c.replyTo ! QueryServiceResponse().withGetFightByIdResponse(GetFightByIdResponse(res)))
                .as(state)
            case c @ GetFightIdsByCategoryIds(competitionId) => FightQueryOperations[LIO]
                .getFightIdsByCategoryIds(competitionId).flatMap(res =>
                  c.replyTo ! QueryServiceResponse().withGetFightIdsByCategoryIdsResponse(
                    GetFightIdsByCategoryIdsResponse(res.view.mapValues(ls => ListOfString(ls)).toMap)
                  )
                ).as(state)
            case c @ GetCategory(competitionId, categoryId) => for {
                res <- (for {
                  category <- OptionT(CompetitionQueryOperations[LIO].getCategoryById(competitionId)(categoryId))
                  periods  <- OptionT.liftF(CompetitionQueryOperations[LIO].getPeriodsByCompetitionId(competitionId))
                  categoryStartTimes = createCategoryIdToStartTimeMap(periods)
                  numberOfFights <- OptionT
                    .liftF(FightQueryOperations[LIO].getNumberOfFightsForCategory(competitionId)(category.id))
                  numberOfCompetitors <- OptionT.liftF(
                    CompetitionQueryOperations[LIO].getNumberOfCompetitorsForCategory(competitionId)(category.id)
                  )
                } yield createCategoryState(
                  competitionId,
                  category,
                  numberOfFights,
                  numberOfCompetitors,
                  categoryStartTimes.get(category.id).map(Timestamps.fromDate).map(Timestamp.fromJavaProto)
                )).value
                _ <- c.replyTo ! QueryServiceResponse().withGetCategoryResponse(GetCategoryResponse(res))
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
                    matState = model.MatState().withMatDescription(DtoMapping.toDtoMat(period.id)(mat)).withNumberOfFights(numberOfFights)
                  } yield (matState, competitors, fights.map(DtoMapping.toDtoFight))
                )
              } yield MatsQueryResult(res.flatMap(_._2), res.map(_._1), res.flatMap(_._3))
              optionRes.value.flatMap(res =>
                c.replyTo ! QueryServiceResponse().withGetPeriodMatsResponse(GetPeriodMatsResponse(
                  Option(res.getOrElse(MatsQueryResult(List.empty, List.empty)))
                ))
              ).as(state)
            case c @ GetPeriodFightsByMats(competitionId, periodId, limit) =>
              import cats.implicits._
              import zio.interop.catz._
              for {
                period <- CompetitionQueryOperations[LIO].getPeriodById(competitionId)(periodId)
                mats = period.map(_.mats).getOrElse(List.empty).map(_.matId)
                fights <- mats
                  .traverse(mat => FightQueryOperations[LIO].getFightsByMat(competitionId)(mat, limit).map(mat -> _))
                _ <- c.replyTo ! QueryServiceResponse().withGetPeriodFightsByMatsResponse(GetPeriodFightsByMatsResponse(
                  fights.map(entry => (entry._1, ListOfString(entry._2.map(_.id)))).toMap
                ))
              } yield state
            case c @ GetFightResulOptions(competitionId, categoryId, stageId) => for {
                stage <- CompetitionQueryOperations[LIO].getStageById(competitionId)(categoryId, stageId)
                fightResultOptions = stage.flatMap(_.stageResultDescriptor)
                  .map(_.fightResultOptions.map(DtoMapping.toDtoFightResultOption)).getOrElse(List.empty)
                _ <- c.replyTo ! QueryServiceResponse()
                  .withGetFightResulOptionsResponse(GetFightResulOptionsResponse(fightResultOptions))
              } yield state
            case c @ GetStagesForCategory(competitionId, categoryId) => CompetitionQueryOperations[LIO]
                .getStagesByCategory(competitionId)(categoryId).flatMap(res =>
                  c.replyTo ! QueryServiceResponse().withGetStagesForCategoryResponse(GetStagesForCategoryResponse(
                    res.map(DtoMapping.toDtoStageDescriptor)
                  ))
                ).as(state)
            case c @ GetStageById(competitionId, categoryId, stageId) => CompetitionQueryOperations[LIO]
                .getStageById(competitionId)(categoryId, stageId).flatMap(res =>
                  c.replyTo ! QueryServiceResponse()
                    .withGetStageByIdResponse(GetStageByIdResponse(res.map(DtoMapping.toDtoStageDescriptor)))
                ).as(state)
            case c @ GetStageFights(competitionId, categoryId, stageId) => FightQueryOperations[LIO]
                .getFightsByStage(competitionId)(categoryId, stageId).map(_.map(DtoMapping.toDtoFight)).flatMap(res =>
                  c.replyTo ! QueryServiceResponse().withGetStageFightsResponse(GetStageFightsResponse(res))
                ).as(state)
          }
        } yield res
      }
    }

  private def createCategoryIdToStartTimeMap(periods: List[Period]) = {
    periods.flatMap(_.scheduleEntries).flatMap(se => se.categoryIds.map(catId => (catId, se.startTime)))
      .filter(_._2.isDefined).map(pair => (pair._1, pair._2.get))
      .groupMapReduce(_._1)(_._2)((date1, date2) => if (date1.before(date2)) date1 else date2)
  }

  private def createGetCompetitorsResponse(res: (List[Competitor], Pagination)) = {
    QueryServiceResponse().withGetCompetitorsResponse(
      GetCompetitorsResponse().withCompetitors(res._1.map(DtoMapping.toDtoCompetitor)).withPageInfo(
        PageInfo().withPage(
          Integer.signum(Integer.bitCount(res._2.maxResults)) * res._2.offset / Math.max(res._2.maxResults, 1)
        ).withTotal(res._2.totalResults)
      )
    )
  }

  private def createCategoryState(
    competitionId: String,
    category: Category,
    numberOfFights: Int,
    numberOfCompetitors: Int,
    startDate: Option[Timestamp]
  ) = {
    model.CategoryState().withCategory(DtoMapping.toDtoCategory(category)).withId(category.id)
      .withCompetitionId(competitionId).withNumberOfCompetitors(numberOfCompetitors).withFightsNumber(numberOfFights)
      .update(_.startDate.setIfDefined(startDate))
  }
}
