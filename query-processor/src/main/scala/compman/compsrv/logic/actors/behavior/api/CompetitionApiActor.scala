package compman.compsrv.logic.actors.behavior.api

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.data.OptionT
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.logic.category.CategoryGenerateService
import compman.compsrv.logic.fight.FightResultOptionConstants
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
import compservice.model.protobuf.model
import compservice.model.protobuf.query.{MatFightsQueryResult, MatsQueryResult, _}
import org.mongodb.scala.MongoClient

import java.util.concurrent.atomic.AtomicReference

object CompetitionApiActor {

  case class Live(mongoClient: MongoClient, mongodbConfig: MongodbConfig) extends ActorContext {
    implicit val competitionQueryOperations: CompetitionQueryOperations[IO] = CompetitionQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val fightQueryOperations: FightQueryOperations[IO] = FightQueryOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
    implicit val managedCompetitionService: ManagedCompetitionService[IO] = ManagedCompetitionsOperations
      .live(mongoClient, mongodbConfig.queryDatabaseName)
  }

  case class Test(
    competitions: AtomicReference[Map[String, ManagedCompetition]],
    competitionProperties: Option[AtomicReference[Map[String, CompetitionProperties]]] = None,
    categories: Option[AtomicReference[Map[String, Category]]] = None,
    competitors: Option[AtomicReference[Map[String, Competitor]]] = None,
    fights: Option[AtomicReference[Map[String, Fight]]] = None,
    periods: Option[AtomicReference[Map[String, Period]]] = None,
    registrationInfo: Option[AtomicReference[Map[String, RegistrationInfo]]] = None,
    stages: Option[AtomicReference[Map[String, StageDescriptor]]] = None
  ) extends ActorContext {
    implicit val competitionQueryOperations: CompetitionQueryOperations[IO] = CompetitionQueryOperations
      .test[IO](competitionProperties, registrationInfo, categories, competitors, periods, stages)
    implicit val fightQueryOperations: FightQueryOperations[IO] = FightQueryOperations.test[IO](fights)
    implicit val managedCompetitionService: ManagedCompetitionService[IO] = ManagedCompetitionsOperations
      .test[IO](competitions)
  }

  trait ActorContext {
    implicit val competitionQueryOperations: CompetitionQueryOperations[IO]
    implicit val fightQueryOperations: FightQueryOperations[IO]
    implicit val managedCompetitionService: ManagedCompetitionService[IO]
    implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
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

  final case class GetFightById(competitionId: String, fightId: String)(
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

  final case class GetFightResulOptions(competitionId: String, stageId: String)(
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
  def behavior(ctx: ActorContext): Behavior[CompetitionApiCommand] = Behaviors.setup { _ =>
    import cats.implicits._
    import ctx._

    Behaviors.receiveMessage {
      case c @ GenerateCategoriesFromRestrictions(restrictions, idTrees, restrictionNames) => {
          for {
            restrictionNamesOrder <- IO(restrictionNames.zipWithIndex.toMap)
            res <- idTrees.traverse(tree =>
              IO(
                CategoryGenerateService
                  .generateCategoriesFromRestrictions(restrictions.toArray, tree, restrictionNamesOrder)
              )
            )
            _ <- IO(
              c.replyTo ! QueryServiceResponse()
                .withGenerateCategoriesFromRestrictionsResponse(GenerateCategoriesFromRestrictionsResponse(res.flatten))
            )
          } yield Behaviors.same[CompetitionApiCommand]
        }.unsafeRunSync()
      case c: GetDefaultRestrictions => IO(DefaultRestrictions.restrictions).map(res =>
          c.replyTo ! QueryServiceResponse().withGetDefaultRestrictionsResponse(GetDefaultRestrictionsResponse(res))
        ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c: GetDefaultFightResults => IO(FightResultOptionConstants.values).map(res =>
          c.replyTo ! QueryServiceResponse().withGetDefaultFightResultsResponse(GetDefaultFightResultsResponse(res))
        ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c: GetAllCompetitions => ManagedCompetitionsOperations.getActiveCompetitions[IO].map(res =>
          c.replyTo ! QueryServiceResponse()
            .withGetAllCompetitionsResponse(GetAllCompetitionsResponse(res.map(DtoMapping.toDtoManagedCompetition)))
        ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetCompetitionProperties(id) => CompetitionQueryOperations[IO].getCompetitionProperties(id)
          .map(_.map(DtoMapping.toDtoCompetitionProperties)).map(res =>
            c.replyTo ! QueryServiceResponse()
              .withGetCompetitionPropertiesResponse(GetCompetitionPropertiesResponse(res))
          ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetCompetitionInfoTemplate(competitionId) => CompetitionQueryOperations[IO]
          .getCompetitionInfoTemplate(competitionId).map(ci => ci.map(c => new String(c.template)).getOrElse(""))
          .map(res =>
            c.replyTo ! QueryServiceResponse()
              .withGetCompetitionInfoTemplateResponse(GetCompetitionInfoTemplateResponse(Option(res)))
          ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetSchedule(competitionId) =>
        import extensions._
        {
          for {
            periods                <- CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId)
            fighsByScheduleEntries <- FightQueryOperations[IO].getFightsByScheduleEntries(competitionId)
            mats = periods.flatMap(period => period.mats.map(DtoMapping.toDtoMat(period.id)))
            dtoPeriods = periods.map(DtoMapping.toDtoPeriod)
              .map(_.enrichWithFightsByScheduleEntries(fighsByScheduleEntries))
            _ <- IO(
              c.replyTo ! QueryServiceResponse().withGetScheduleResponse(GetScheduleResponse(
                Some(model.Schedule().withId(competitionId).withMats(mats).withPeriods(dtoPeriods))
              ))
            )
          } yield Behaviors.same[CompetitionApiCommand]
        }.unsafeRunSync()
      case c @ GetCompetitors(competitionId, categoryId, searchString, pagination) => categoryId match {
          case Some(value) => CompetitionQueryOperations[IO]
              .getCompetitorsByCategoryId(competitionId)(value, pagination, searchString)
              .map(res => c.replyTo ! createGetCompetitorsResponse(res)).as(Behaviors.same[CompetitionApiCommand])
              .unsafeRunSync()
          case None => CompetitionQueryOperations[IO]
              .getCompetitorsByCompetitionId(competitionId)(pagination, searchString)
              .map(res => c.replyTo ! createGetCompetitorsResponse(res)).as(Behaviors.same[CompetitionApiCommand])
              .unsafeRunSync()
        }
      case c @ GetCompetitor(competitionId, competitorId) => CompetitionQueryOperations[IO]
          .getCompetitorById(competitionId)(competitorId).map(res =>
            c.replyTo ! QueryServiceResponse()
              .withGetCompetitorResponse(GetCompetitorResponse(res.map(DtoMapping.toDtoCompetitor)))
          ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetDashboard(competitionId) => CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId)
          .map(res =>
            c.replyTo ! QueryServiceResponse()
              .withGetDashboardResponse(GetDashboardResponse(res.map(DtoMapping.toDtoPeriod)))
          ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetMats(competitionId) => CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId)
          .map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id))))
          .map(res => c.replyTo ! QueryServiceResponse().withGetMatsResponse(GetMatsResponse(res)))
          .as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetMat(competitionId, matId) => CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId)
          .map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id))).find(_.id == matId))
          .map(res => c.replyTo ! QueryServiceResponse().withGetMatResponse(GetMatResponse(res)))
          .as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetMatFights(competitionId, matId) => {
          for {
            fights <- FightQueryOperations[IO].getFightsByMat(competitionId)(matId, 20)
            fightDtos = fights.map(DtoMapping.toDtoFight)
            competitors = fights.flatMap(_.scores).filter(_.competitorId.isDefined).map(cs =>
              CompetitorDisplayInfo(
                cs.competitorId.orNull,
                cs.competitorFirstName,
                cs.competitorLastName,
                cs.competitorAcademyName
              )
            ).map(DtoMapping.toDtoCompetitor)
            _ <- IO(
              c.replyTo ! QueryServiceResponse()
                .withGetMatFightsResponse(GetMatFightsResponse(Some(MatFightsQueryResult(competitors, fightDtos))))
            )
          } yield Behaviors.same[CompetitionApiCommand]
        }.unsafeRunSync()
      case c @ GetRegistrationInfo(competitionId) => {
          for {
            regInfo <- CompetitionQueryOperations[IO].getRegistrationInfo(competitionId)
            _ <- IO {
              c.replyTo ! QueryServiceResponse().withGetRegistrationInfoResponse(GetRegistrationInfoResponse(
                regInfo.map(DtoMapping.toDtoRegistrationInfo)
              ))
            }
          } yield Behaviors.same[CompetitionApiCommand]
        }.unsafeRunSync()
      case c @ GetCategories(competitionId) => {
          for {
            categories <- CompetitionQueryOperations[IO].getCategoriesByCompetitionId(competitionId)
            periods    <- CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId)
            categoryStartTimes = createCategoryIdToStartTimeMap(periods)
            categoryStates <- categories.traverse { category =>
              for {
                numberOfFights <- FightQueryOperations[IO].getNumberOfFightsForCategory(competitionId)(category.id)
                numberOfCompetitors <- CompetitionQueryOperations[IO]
                  .getNumberOfCompetitorsForCategory(competitionId)(category.id)
              } yield createCategoryState(
                competitionId,
                category,
                numberOfFights,
                numberOfCompetitors,
                categoryStartTimes.get(category.id).map(Timestamps.fromDate).map(Timestamp.fromJavaProto)
              )
            }
            _ <-
              IO { c.replyTo ! QueryServiceResponse().withGetCategoriesResponse(GetCategoriesResponse(categoryStates)) }
          } yield Behaviors.same[CompetitionApiCommand]
        }.unsafeRunSync()
      case c @ GetFightById(competitionId, fightId) => FightQueryOperations[IO].getFightById(competitionId)(fightId)
          .map(_.map(DtoMapping.toDtoFight))
          .map(res => c.replyTo ! QueryServiceResponse().withGetFightByIdResponse(GetFightByIdResponse(res)))
          .as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetFightIdsByCategoryIds(competitionId) => FightQueryOperations[IO]
          .getFightIdsByCategoryIds(competitionId).map(res =>
            c.replyTo ! QueryServiceResponse().withGetFightIdsByCategoryIdsResponse(GetFightIdsByCategoryIdsResponse(
              res.view.mapValues(ls => ListOfString(ls)).toMap
            ))
          ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetCategory(competitionId, categoryId) => {
          for {
            res <- (for {
              category <- OptionT(CompetitionQueryOperations[IO].getCategoryById(competitionId)(categoryId))
              periods  <- OptionT.liftF(CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId))
              categoryStartTimes = createCategoryIdToStartTimeMap(periods)
              numberOfFights <- OptionT
                .liftF(FightQueryOperations[IO].getNumberOfFightsForCategory(competitionId)(category.id))
              numberOfCompetitors <- OptionT
                .liftF(CompetitionQueryOperations[IO].getNumberOfCompetitorsForCategory(competitionId)(category.id))
            } yield createCategoryState(
              competitionId,
              category,
              numberOfFights,
              numberOfCompetitors,
              categoryStartTimes.get(category.id).map(Timestamps.fromDate).map(Timestamp.fromJavaProto)
            )).value
            _ <- IO { c.replyTo ! QueryServiceResponse().withGetCategoryResponse(GetCategoryResponse(res)) }
          } yield Behaviors.same[CompetitionApiCommand]
        }.unsafeRunSync()

      case c @ GetPeriodMats(competitionId, periodId) =>
        val optionRes = for {
          period <- OptionT(CompetitionQueryOperations[IO].getPeriodById(competitionId)(periodId))
          mats = period.mats
          res <- mats.traverse(mat =>
            for {
              fights <- OptionT.liftF(FightQueryOperations[IO].getFightsByMat(competitionId)(mat.matId, 10))
              numberOfFights <- OptionT
                .liftF(FightQueryOperations[IO].getNumberOfFightsForMat(competitionId)(mat.matId))
              competitors = fights.flatMap(_.scores).filter(_.competitorId.isDefined).map(cs =>
                CompetitorDisplayInfo(
                  cs.competitorId.get,
                  cs.competitorFirstName,
                  cs.competitorLastName,
                  cs.competitorAcademyName
                )
              ).map(DtoMapping.toDtoCompetitor)
              matState = model.MatState().withMatDescription(DtoMapping.toDtoMat(period.id)(mat))
                .withNumberOfFights(numberOfFights)
            } yield (matState, competitors, fights.map(DtoMapping.toDtoFight))
          )
        } yield MatsQueryResult(res.flatMap(_._2), res.map(_._1), res.flatMap(_._3))
        optionRes.value.map(res =>
          c.replyTo ! QueryServiceResponse().withGetPeriodMatsResponse(GetPeriodMatsResponse(
            Option(res.getOrElse(MatsQueryResult(List.empty, List.empty)))
          ))
        ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetPeriodFightsByMats(competitionId, periodId, limit) =>
        import cats.implicits._
        {
          for {
            period <- CompetitionQueryOperations[IO].getPeriodById(competitionId)(periodId)
            mats = period.map(_.mats).getOrElse(List.empty).map(_.matId)
            fights <- mats
              .traverse(mat => FightQueryOperations[IO].getFightsByMat(competitionId)(mat, limit).map(mat -> _))
            _ <- IO {
              c.replyTo ! QueryServiceResponse().withGetPeriodFightsByMatsResponse(GetPeriodFightsByMatsResponse(
                fights.map(entry => (entry._1, ListOfString(entry._2.map(_.id)))).toMap
              ))

            }
          } yield Behaviors.same[CompetitionApiCommand]
        }.unsafeRunSync()
      case c @ GetFightResulOptions(competitionId, stageId) => {
          for {
            stage <- CompetitionQueryOperations[IO].getStageById(competitionId)(stageId)
            fightResultOptions = stage.flatMap(_.stageResultDescriptor)
              .map(_.fightResultOptions.map(DtoMapping.toDtoFightResultOption)).getOrElse(List.empty)
            _ <- IO {
              c.replyTo ! QueryServiceResponse()
                .withGetFightResulOptionsResponse(GetFightResulOptionsResponse(fightResultOptions))
            }
          } yield Behaviors.same[CompetitionApiCommand]
        }.unsafeRunSync()
      case c @ GetStagesForCategory(competitionId, categoryId) => CompetitionQueryOperations[IO]
          .getStagesByCategory(competitionId)(categoryId).map(res =>
            c.replyTo ! QueryServiceResponse()
              .withGetStagesForCategoryResponse(GetStagesForCategoryResponse(res.map(DtoMapping.toDtoStageDescriptor)))
          ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetStageById(competitionId, _, stageId) => CompetitionQueryOperations[IO]
          .getStageById(competitionId)(stageId).map(res =>
            c.replyTo ! QueryServiceResponse()
              .withGetStageByIdResponse(GetStageByIdResponse(res.map(DtoMapping.toDtoStageDescriptor)))
          ).as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
      case c @ GetStageFights(competitionId, categoryId, stageId) => FightQueryOperations[IO]
          .getFightsByStage(competitionId)(categoryId, stageId).map(_.map(DtoMapping.toDtoFight))
          .map(res => c.replyTo ! QueryServiceResponse().withGetStageFightsResponse(GetStageFightsResponse(res)))
          .as(Behaviors.same[CompetitionApiCommand]).unsafeRunSync()
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
