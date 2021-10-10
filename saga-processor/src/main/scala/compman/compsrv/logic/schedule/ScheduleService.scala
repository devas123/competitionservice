package compman.compsrv.logic.schedule

import cats.Monad
import cats.data.EitherT
import cats.implicits._
import compman.compsrv.logic._
import compman.compsrv.logic.Operations.IdOperations
import compman.compsrv.logic.fights.CanFail
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.{FightStartTimePairDTO, PeriodDTO, ScheduleDTO}
import compman.compsrv.model.extensions._

object ScheduleService {
  def generateSchedule[F[+_]: Monad: IdOperations](
    competitionId: String,
    periods: Seq[PeriodDTO],
    mats: Seq[MatDescriptionDTO],
    stageGraph: StageGraph,
    timeZone: String
  ): F[CanFail[(ScheduleDTO, List[FightStartTimePairDTO])]] = {
    for {
      periodsWithIds <- EitherT
        .liftF(periods.traverse(p => IdOperations[F].generateIdIfMissing(Option(p.getId)).map(p.setId)))
      sortedPeriods = periodsWithIds.sortBy(_.getStartTime)
      enrichedScheduleRequirements <- EitherT.liftF(
        periodsWithIds.flatMap { periodDTO =>
          periodDTO.getScheduleRequirements.map { it => it.setPeriodId(periodDTO.getId) }
        }.traverse { it => IdOperations[F].generateIdIfMissing(Option(it.getId)).map(it.setId) }.map(_.sortBy {
          _.getEntryOrder
        })
      )
      flatFights = enrichedScheduleRequirements.flatMap(_.fightIdsOrEmpty)
      _ <- assertET[F](flatFights.size == flatFights.distinct.size, Some("Duplicate fights detected"))
      requirementsGraph <- EitherT.fromEither[F](RequirementsGraph.create(
        enrichedScheduleRequirements.groupMapReduce(_.getId)(identity)((a, _) => a),
        stageGraph.getCategoryIdsToFightIds,
        sortedPeriods.map(_.getId).toArray
      ))
      result <- EitherT.fromEither[F](ScheduleProducer.simulate(
        stageGraph,
        requirementsGraph,
        sortedPeriods.toList,
        enrichedScheduleRequirements.toList,
        sortedPeriods.map(p => p.getId -> p.getStartTime).toMap,
        mats.toList,
        timeZone
      ))
      invalidFights = result._3
      fightsStartTimes = result._2.flatMap { container =>
        container.fights.map { it =>
          new FightStartTimePairDTO().setStartTime(it.startTime).setNumberOnMat(it.fightNumber).setFightId(it.fightId)
            .setPeriodId(it.periodId).setFightCategoryId(it.categoryId).setMatId(it.matId)
            .setScheduleEntryId(it.scheduleEntryId).setInvalid(invalidFights.contains(it.fightId))
        }
      }
      scheduleEntriesByPeriod      = result._1.groupBy(_.getPeriodId)
      scheduleRequirementsByPeriod = enrichedScheduleRequirements.groupBy(_.getPeriodId)
    } yield new ScheduleDTO().setId(competitionId).setMats(mats.toArray).setPeriods(
      sortedPeriods.map(period =>
        period.setScheduleRequirements(scheduleRequirementsByPeriod.getOrElse(period.getId, List.empty).toArray)
          .setScheduleEntries(
            scheduleEntriesByPeriod.getOrElse(period.getId, List.empty).sortBy(_.getStartTime)
              .mapWithIndex((e, ind) => e.setOrder(ind).setCategoryIds(e.getCategoryIds.distinct)).toArray
          )
      ).toArray
    ) -> fightsStartTimes
  }.value
}
