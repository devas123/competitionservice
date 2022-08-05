package compman.compsrv.logic.actors.behavior.api

import akka.actor.typed.{scaladsl, ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import cats.data.OptionT
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.util.Timestamps
import compman.compsrv.logic.actors.behavior.WithIORuntime
import compman.compsrv.logic.actors.behavior.api.CompetitionApiCommands._
import compman.compsrv.logic.category.CategoryGenerateService
import compman.compsrv.logic.fight.FightResultOptionConstants
import compman.compsrv.query.config.MongodbConfig
import compman.compsrv.query.model._
import compman.compsrv.query.model.mapping.DtoMapping
import compman.compsrv.query.service.repository.{CompetitionQueryOperations, FightQueryOperations, ManagedCompetitionsOperations, Pagination}
import compman.compsrv.query.service.repository.ManagedCompetitionsOperations.ManagedCompetitionService
import compservice.model.protobuf.model
import compservice.model.protobuf.query.{MatFightsQueryResult, MatsQueryResult, _}
import org.mongodb.scala.MongoClient

import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import scala.concurrent.duration.DurationInt

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

  trait ActorContext extends WithIORuntime {
    implicit val competitionQueryOperations: CompetitionQueryOperations[IO]
    implicit val fightQueryOperations: FightQueryOperations[IO]
    implicit val managedCompetitionService: ManagedCompetitionService[IO]
  }

  def behavior(ctx: ActorContext): Behavior[CompetitionApiCommand] = Behaviors.setup { context =>
    import cats.implicits._
    import ctx._

    Behaviors.receiveMessage {
      case c @ GenerateCategoriesFromRestrictions(restrictions, idTrees, restrictionNames) =>
        val io = for {
          restrictionNamesOrder <- IO(restrictionNames.zipWithIndex.toMap)
          res <- idTrees.traverse(tree =>
            IO(
              CategoryGenerateService
                .generateCategoriesFromRestrictions(restrictions.toArray, tree, restrictionNamesOrder)
            )
          )
        } yield QueryServiceResponse().withGenerateCategoriesFromRestrictionsResponse(GenerateCategoriesFromRestrictionsResponse(res.flatten))
        runEffectAndReply(context, c.replyTo, io)

      case c: GetDefaultRestrictions =>
        val io = IO(DefaultRestrictions.restrictions)
          .map(res => QueryServiceResponse().withGetDefaultRestrictionsResponse(GetDefaultRestrictionsResponse(res)))
        runEffectAndReply(context, c.replyTo, io)

      case c: GetDefaultFightResults =>
        val io = IO(FightResultOptionConstants.values)
          .map(res => QueryServiceResponse().withGetDefaultFightResultsResponse(GetDefaultFightResultsResponse(res)))
        runEffectAndReply(context, c.replyTo, io)

      case c: GetAllCompetitions =>
        val io = ManagedCompetitionsOperations.getActiveCompetitions[IO].map(res =>
          QueryServiceResponse()
            .withGetAllCompetitionsResponse(GetAllCompetitionsResponse(res.map(DtoMapping.toDtoManagedCompetition)))
        )
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetCompetitionProperties(id) =>
        val io = CompetitionQueryOperations[IO].getCompetitionProperties(id)
          .map(_.map(DtoMapping.toDtoCompetitionProperties)).map(res =>
            QueryServiceResponse().withGetCompetitionPropertiesResponse(GetCompetitionPropertiesResponse(res))
          )
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetCompetitionInfoTemplate(competitionId) =>
        val io = CompetitionQueryOperations[IO].getCompetitionInfoTemplate(competitionId)
          .map(ci => ci.map(c => new String(c.template)).getOrElse("")).map(res =>
            QueryServiceResponse()
              .withGetCompetitionInfoTemplateResponse(GetCompetitionInfoTemplateResponse(Option(res)))
          )
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetSchedule(competitionId) =>
        import extensions._
        val io = for {
          periods                <- CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId)
          fighsByScheduleEntries <- FightQueryOperations[IO].getFightsByScheduleEntries(competitionId)
          mats = periods.flatMap(period => period.mats.map(DtoMapping.toDtoMat(period.id)))
          dtoPeriods = periods.map(DtoMapping.toDtoPeriod)
            .map(_.enrichWithFightsByScheduleEntries(fighsByScheduleEntries))
        } yield QueryServiceResponse().withGetScheduleResponse(GetScheduleResponse(Some(model.Schedule().withId(competitionId).withMats(mats).withPeriods(dtoPeriods))))
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetCompetitors(competitionId, categoryId, searchString, pagination) =>
        val io = categoryId match {
          case Some(value) => CompetitionQueryOperations[IO]
              .getCompetitorsByCategoryId(competitionId)(value, pagination, searchString)
              .map(res => createGetCompetitorsResponse(res))

          case None => CompetitionQueryOperations[IO]
              .getCompetitorsByCompetitionId(competitionId)(pagination, searchString)
              .map(res => createGetCompetitorsResponse(res))

        }
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetCompetitor(competitionId, competitorId) =>
        val io = CompetitionQueryOperations[IO].getCompetitorById(competitionId)(competitorId).map(res =>
          QueryServiceResponse().withGetCompetitorResponse(GetCompetitorResponse(res.map(DtoMapping.toDtoCompetitor)))
        )
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetDashboard(competitionId) =>
        val io = CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId).map(res =>
          QueryServiceResponse().withGetDashboardResponse(GetDashboardResponse(res.map(DtoMapping.toDtoPeriod)))
        )
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetMats(competitionId) =>
        val io = CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId)
          .map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id))))
          .map(res => QueryServiceResponse().withGetMatsResponse(GetMatsResponse(res)))

        runEffectAndReply(context, c.replyTo, io)

      case c @ GetMat(competitionId, matId) =>
        val io = CompetitionQueryOperations[IO].getPeriodsByCompetitionId(competitionId)
          .map(_.flatMap(p => p.mats.map(DtoMapping.toDtoMat(p.id))).find(_.id == matId))
          .map(res => QueryServiceResponse().withGetMatResponse(GetMatResponse(res)))

        runEffectAndReply(context, c.replyTo, io)

      case c @ GetMatFights(competitionId, matId) =>
        val io = for {
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
        } yield QueryServiceResponse().withGetMatFightsResponse(GetMatFightsResponse(Some(MatFightsQueryResult(competitors, fightDtos))))
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetRegistrationInfo(competitionId) =>
        val io =
          for {
            regInfo <- CompetitionQueryOperations[IO].getRegistrationInfo(competitionId)
          } yield QueryServiceResponse()
            .withGetRegistrationInfoResponse(GetRegistrationInfoResponse(regInfo.map(DtoMapping.toDtoRegistrationInfo)))

        runEffectAndReply(context, c.replyTo, io)

      case c @ GetCategories(competitionId) =>
        val io = for {
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
        } yield QueryServiceResponse().withGetCategoriesResponse(GetCategoriesResponse(categoryStates))
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetFightById(competitionId, fightId) =>
        val io = FightQueryOperations[IO].getFightById(competitionId)(fightId).map(_.map(DtoMapping.toDtoFight))
          .map(res => QueryServiceResponse().withGetFightByIdResponse(GetFightByIdResponse(res)))
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetFightIdsByCategoryIds(competitionId) =>
        val io = FightQueryOperations[IO].getFightIdsByCategoryIds(competitionId).map(res =>
          QueryServiceResponse().withGetFightIdsByCategoryIdsResponse(GetFightIdsByCategoryIdsResponse(
            res.view.mapValues(ls => ListOfString(ls)).toMap
          ))
        )
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetCategory(competitionId, categoryId) =>
        val io = for {
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
        } yield QueryServiceResponse().withGetCategoryResponse(GetCategoryResponse(res))
        runEffectAndReply(context, c.replyTo, io)

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
        val io = optionRes.value.map(res =>
          QueryServiceResponse().withGetPeriodMatsResponse(GetPeriodMatsResponse(
            Option(res.getOrElse(MatsQueryResult(List.empty, List.empty)))
          ))
        )
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetPeriodFightsByMats(competitionId, periodId, limit) =>
        import cats.implicits._
        val io = for {
          period <- CompetitionQueryOperations[IO].getPeriodById(competitionId)(periodId)
          mats = period.map(_.mats).getOrElse(List.empty).map(_.matId)
          fights <- mats
            .traverse(mat => FightQueryOperations[IO].getFightsByMat(competitionId)(mat, limit).map(mat -> _))
        } yield QueryServiceResponse().withGetPeriodFightsByMatsResponse(GetPeriodFightsByMatsResponse(fights.map(entry => (entry._1, ListOfString(entry._2.map(_.id)))).toMap))
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetFightResulOptions(competitionId, stageId) =>
        val io = for {
          stage <- CompetitionQueryOperations[IO].getStageById(competitionId)(stageId)
          fightResultOptions = stage.flatMap(_.stageResultDescriptor)
            .map(_.fightResultOptions.map(DtoMapping.toDtoFightResultOption)).getOrElse(List.empty)
        } yield QueryServiceResponse().withGetFightResulOptionsResponse(GetFightResulOptionsResponse(fightResultOptions))
        runEffectAndReply(context, c.replyTo, io)
      case c @ GetStagesForCategory(competitionId, categoryId) =>
        val io = CompetitionQueryOperations[IO].getStagesByCategory(competitionId)(categoryId).map(res =>
          QueryServiceResponse()
            .withGetStagesForCategoryResponse(GetStagesForCategoryResponse(res.map(DtoMapping.toDtoStageDescriptor)))
        )
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetStageById(competitionId, _, stageId) =>
        val io = CompetitionQueryOperations[IO].getStageById(competitionId)(stageId).map(res =>
          QueryServiceResponse()
            .withGetStageByIdResponse(GetStageByIdResponse(res.map(DtoMapping.toDtoStageDescriptor)))
        )
        runEffectAndReply(context, c.replyTo, io)

      case c @ GetStageFights(competitionId, categoryId, stageId) =>
        val io = FightQueryOperations[IO].getFightsByStage(competitionId)(categoryId, stageId)
          .map(_.map(DtoMapping.toDtoFight))
          .map(res => QueryServiceResponse().withGetStageFightsResponse(GetStageFightsResponse(res)))
        runEffectAndReply(context, c.replyTo, io)
    }
  }

  private def runEffectAndReply(
    context: scaladsl.ActorContext[CompetitionApiCommand],
    replyTo: ActorRef[QueryServiceResponse],
    io: IO[QueryServiceResponse]
  )(implicit runtime: IORuntime) = {
    context.spawn(
      QueryServiceRequestEffectExecutor.behavior(io, replyTo, 10.seconds),
      s"Effect-executor-${UUID.randomUUID()}"
    )
    Behaviors.same[CompetitionApiCommand]
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
