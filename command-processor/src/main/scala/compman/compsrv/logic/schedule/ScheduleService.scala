package compman.compsrv.logic.schedule

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.Utils
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.IdOperations
import compman.compsrv.logic.fight.CanFail
import compman.compsrv.model.extensions._
import compservice.model.protobuf.model._

object ScheduleService {
  def generateSchedule[F[+_]: Monad: IdOperations](
    competitionId: String,
    periods: Seq[Period],
    mats: Seq[MatDescription],
    stageGraph: StageGraph,
    timeZone: String
  ): F[CanFail[(Schedule, List[FightStartTimePair])]] = {
    for {
      periodsWithIds <- EitherT
        .liftF(periods.traverse(p => IdOperations[F].generateIdIfMissing(Option(p.id)).map(p.withId)))
      sortedPeriods = periodsWithIds.sortBy(_.getStartTime)
      enrichedScheduleRequirements <- EitherT.liftF(
        periodsWithIds.flatMap { periodDTO =>
          periodDTO.scheduleRequirements.map { it => it.withPeriodId(periodDTO.id) }
        }.traverse { it => IdOperations[F].generateIdIfMissing(Option(it.id)).map(it.withId) }.map(_.sortBy {
          _.entryOrder
        })
      )
      flatFights = enrichedScheduleRequirements.flatMap(_.fightIdsOrEmpty)
      _ <- assertET[F](flatFights.size == flatFights.distinct.size, Some("Duplicate fights detected"))
      requirementsGraph <- EitherT.fromEither[F](RequirementsGraph(
        Utils.groupById(enrichedScheduleRequirements)(_.id),
        stageGraph.getCategoryIdsToFightIds,
        sortedPeriods.map(_.id).toArray
      ))
      result <- EitherT.fromEither[F](ScheduleProducer.simulate(
        stageGraph,
        requirementsGraph,
        sortedPeriods.toList,
        enrichedScheduleRequirements.toList,
        sortedPeriods.map(p => p.id -> p.getStartTime.asJavaInstant).toMap,
        mats.toList,
        timeZone
      ))
      invalidFights = result._3
      fightsStartTimes = result._2.flatMap { container =>
        container.fights.map { it =>
          FightStartTimePair()
            .withStartTime(it.startTime.asTimestamp)
            .withNumberOnMat(it.fightNumber)
            .withFightId(it.fightId)
            .withPeriodId(it.periodId)
            .withFightCategoryId(it.categoryId)
            .withMatId(it.matId)
            .withScheduleEntryId(it.scheduleEntryId)
            .withInvalid(invalidFights.contains(it.fightId))
        }
      }
      scheduleEntriesByPeriod      = result._1.groupBy(_.periodId)
      scheduleRequirementsByPeriod = enrichedScheduleRequirements.groupBy(_.periodId)
    } yield Schedule()
      .withId(competitionId)
      .withMats(mats)
      .withPeriods(
      sortedPeriods.map(period =>
        period
          .withScheduleRequirements(scheduleRequirementsByPeriod.getOrElse(period.id, List.empty).toArray)
          .withScheduleEntries(
            scheduleEntriesByPeriod.getOrElse(period.id, List.empty).sortBy(_.getStartTime)
              .mapWithIndex((e, ind) => e.withOrder(ind).withCategoryIds(e.categoryIds.distinct)).toArray
          )
      ).toArray
    ) -> fightsStartTimes
  }.value
}
