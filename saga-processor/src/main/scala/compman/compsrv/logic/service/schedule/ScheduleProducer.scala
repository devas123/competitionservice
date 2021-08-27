package compman.compsrv.logic.service.schedule

import compman.compsrv.logic.service.fights.CanFail
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule._
import compman.compsrv.model.extension._

import java.time.{Instant, ZonedDateTime, ZoneId}
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try
import cats.implicits._
import compman.compsrv.model.Errors

object ScheduleProducer {
  private def eightyPercentOfDurationInMillis(duration: Long): Long = duration * 8 / 10

  private def createPauseEntry(
    pauseReq: ScheduleRequirementDTO,
    startTime: Instant,
    endTime: Instant,
    pauseType: ScheduleEntryType
  ): ScheduleEntryDTO = {
    val entry = new ScheduleEntryDTO()
    entry.setId(pauseReq.getId)
    entry.setCategoryIds(Array.empty)
    entry.setFightIds(Array(
      new MatIdAndSomeId().setSomeId(pauseReq.getId).setMatId(pauseReq.getMatId).setStartTime(startTime)
    ))
    entry.setPeriodId(pauseReq.getPeriodId)
    entry.setStartTime(startTime)
    entry.setNumberOfFights(0)
    entry.setEntryType(pauseType)
    entry.setEndTime(endTime)
    entry.setDuration(pauseReq.getDurationSeconds)
    entry.setRequirementIds(Array(pauseReq.getId))
  }

  private def createFixedPauseEntry(fixedPause: ScheduleRequirementDTO, endTime: Instant) =
    createPauseEntry(fixedPause, fixedPause.getStartTime, endTime, ScheduleEntryType.FIXED_PAUSE)

  private def createRelativePauseEntry(requirement: ScheduleRequirementDTO, startTime: Instant, endTime: Instant) =
    createPauseEntry(requirement, startTime, endTime, ScheduleEntryType.RELATIVE_PAUSE)

  private def getFightDuration(duration: Int, riskCoeff: Int, timeBetweenFights: Int): Int = duration *
    (1 + 100 / riskCoeff) + timeBetweenFights

  def simulate(
    stageGraph: StageGraph,
    requiremetsGraph: RequirementsGraph,
    periods: List[PeriodDTO],
    req: List[ScheduleRequirementDTO],
    periodStartTime: Map[String, Instant],
    mats: List[MatDescriptionDTO],
    timeZone: String
  ): CanFail[(List[ScheduleEntryDTO], List[InternalMatScheduleContainer], Set[String])] = Try {
    val initialFightsByMats: List[InternalMatScheduleContainer] =
      createMatScheduleContainers(mats, periodStartTime, timeZone)
    val accumulator: ScheduleAccumulator = ScheduleAccumulator(initialFightsByMats)
    val pauses = req.filter { _.getEntryType == ScheduleRequirementType.FIXED_PAUSE }.groupBy { _.getMatId }.view
      .mapValues { e => e.sortBy { _.getStartTime.toEpochMilli() } }.mapValues(ArrayBuffer.from(_))
    val unfinishedRequirements = mutable.Queue.empty[ScheduleRequirementDTO]
    val matsToIds              = accumulator.matSchedules.groupMapReduce(_.id)(identity)((a, _) => a)
    val sortedPeriods          = periods.sortBy { _.getStartTime }
    val requirementsCapacity   = requiremetsGraph.requirementFightsSize

    def loadBalanceToMats(
      req: (ScheduleRequirementDTO, List[String]),
      periodMats: Seq[InternalMatScheduleContainer],
      requirementsCapacity: Array[Int],
      requiremetsGraph: RequirementsGraph,
      accumulator: ScheduleAccumulator,
      st: StageGraph,
      period: PeriodDTO
    ): StageGraph = {
      req._2.foldLeft(st) { (stageGraph, fightId) =>
        val mat = periodMats.minBy { _.currentTime.toEpochMilli() }
        updateMatAndSchedule(requirementsCapacity, requiremetsGraph, req, accumulator, mat, fightId, stageGraph, period)
      }
    }

    def updateMatAndSchedule(
      requirementsCapacity: Array[Int],
      requiremetsGraph: RequirementsGraph,
      req: (ScheduleRequirementDTO, List[String]),
      accumulator: ScheduleAccumulator,
      mat: InternalMatScheduleContainer,
      fightId: String,
      st: StageGraph,
      period: PeriodDTO
    ): StageGraph = {
      val duration = getFightDuration(
        st.getDuration(fightId).toInt,
        (period.getRiskPercent.doubleValue() * 100).toInt,
        period.getTimeBetweenFights
      )
      if (
        !(pauses(mat.id) == null || pauses(mat.id).isEmpty) &&
        mat.currentTime.toEpochMilli + eightyPercentOfDurationInMillis(duration) >= pauses(mat.id)(0)
          .getStartTime.toEpochMilli
      ) {
        val p = pauses(mat.id).remove(0)
        val e = createFixedPauseEntry(p, p.getStartTime.plus(p.getDurationSeconds.toLong, ChronoUnit.SECONDS))
        accumulator.scheduleEntries.append(e)
        mat.currentTime = mat.currentTime.plus(p.getDurationSeconds.toLong, ChronoUnit.SECONDS)
      }
      requirementsCapacity(requiremetsGraph.getIndex(req._1.getId).getOrElse(-1)) -= 1
      val e = accumulator.scheduleEntryFromRequirement(req._1, mat.currentTime, Option(period.getId))
      accumulator.scheduleEntries(e).addCategoryId(st.getCategoryId(fightId))
      mat.fights.append(InternalFightStartTime(
        fightId,
        st.getCategoryId(fightId),
        mat.id,
        mat.totalFights,
        mat.currentTime,
        accumulator.scheduleEntries(e).getId,
        period.getId
      ))
      mat.totalFights += 1
      mat.currentTime = mat.currentTime.plus(duration.toLong, ChronoUnit.SECONDS)
      StageGraph.completeFight(fightId, st)
    }

    def addRequirementsToQueue(
      queueToUpdate: mutable.Queue[(ScheduleRequirementDTO, List[String])],
      requirementsQueue: mutable.Queue[ScheduleRequirementDTO]
    ): Unit = {
      var i = 0
      while ((requirementsQueue.nonEmpty && i < initialFightsByMats.size) || unfinishedRequirements.nonEmpty) {
        val sr =
          if (unfinishedRequirements.nonEmpty) unfinishedRequirements.dequeue()
          else if (requirementsQueue.nonEmpty) {
            i += 1
            requirementsQueue.dequeue()
          } else { return }
        if (
          sr.getEntryType == ScheduleRequirementType.RELATIVE_PAUSE && sr.getMatId != null && sr.getDurationSeconds > 0
        ) { queueToUpdate.append((sr, List.empty)) }
        else {
          val i1 = requiremetsGraph.getIndex(sr.getId).getOrElse(-1)
          if (i1 >= 0 && requirementsCapacity(i1) > 0) {
            val fights = stageGraph.flushCompletableFights(requiremetsGraph.getFightIdsForRequirement(sr.getId))
            queueToUpdate.append((sr, fights))
          }
        }
      }

    }

    def dispatchFightsFromQueue(
      period: PeriodDTO,
      periodMats: mutable.Seq[InternalMatScheduleContainer],
      q: mutable.Queue[(ScheduleRequirementDTO, List[String])]
    ): Unit = {
      while (q.nonEmpty) {
        val req = q.dequeue()
        if (
          req._1.getEntryType == ScheduleRequirementType.RELATIVE_PAUSE && req._1.getMatId != null &&
          req._1.getDurationSeconds != 0 && req._1.getPeriodId == period.getId
        ) {
          if (matsToIds.contains(req._1.getMatId) && matsToIds(req._1.getMatId).periodId == period.getId) {
            val mat = matsToIds(req._1.getMatId)
            val e = createRelativePauseEntry(
              req._1,
              mat.currentTime,
              mat.currentTime.plus(req._1.getDurationSeconds.toLong, ChronoUnit.SECONDS)
            )
            accumulator.scheduleEntries.append(e)
            mat.currentTime = mat.currentTime.plus(req._1.getDurationSeconds.toLong, ChronoUnit.SECONDS)
          }
        } else {
          val ind      = requiremetsGraph.getIndex(req._1.getId)
          val capacity = requirementsCapacity(ind.getOrElse(-1))
          if (req._1.getMatId != null) {
            if (matsToIds.contains(req._1.getMatId)) {
              val mat = matsToIds(req._1.getMatId)
              if (mat.periodId == period.getId) {
                loadBalanceToMats(
                  req,
                  Seq(mat),
                  requirementsCapacity,
                  requiremetsGraph,
                  accumulator,
                  stageGraph,
                  period
                )
              } else {
                loadBalanceToMats(
                  req,
                  periodMats.toSeq,
                  requirementsCapacity,
                  requiremetsGraph,
                  accumulator,
                  stageGraph,
                  period
                )
              }
            }
          } else {
            loadBalanceToMats(
              req,
              periodMats.toSeq,
              requirementsCapacity,
              requiremetsGraph,
              accumulator,
              stageGraph,
              period
            )
          }

          if (capacity > 0 && capacity == requirementsCapacity(requiremetsGraph.getIndexOrMinus1(req._1.getId))) {
            unfinishedRequirements.enqueue(req._1)
          } else if (requirementsCapacity(requiremetsGraph.getIndexOrMinus1(req._1.getId)) > 0) {
            q.enqueue(
              (req._1, stageGraph.flushCompletableFights(requiremetsGraph.getFightIdsForRequirement(req._1.getId)))
            )
          }
        }
      }
    }

    sortedPeriods.foreach { period =>
      val periodMats = accumulator.matSchedules.filter { _.periodId == period.getId }
      unfinishedRequirements.foreach { r =>
        stageGraph.flushNonCompletedFights(requiremetsGraph.getFightIdsForRequirement(r.getId)).foreach { it =>
          accumulator.invalidFights.add(it)
        }
      }
      val q: mutable.Queue[(ScheduleRequirementDTO, List[String])] = mutable.Queue.empty
      val rq: mutable.Queue[ScheduleRequirementDTO] = mutable.Queue
        .from(requiremetsGraph.orderedRequirements.filter { it => it.getPeriodId == period.getId })
      var dispatchSuccessful = true
      while ((rq.nonEmpty || unfinishedRequirements.nonEmpty) && dispatchSuccessful) {
        val n              = stageGraph.getNonCompleteCount
        val onlyUnfinished = rq.isEmpty
        addRequirementsToQueue(q, rq)
        dispatchFightsFromQueue(period, periodMats, q)
        dispatchSuccessful = onlyUnfinished && n == stageGraph.getNonCompleteCount
      }
    }
    (
      accumulator.scheduleEntries.toList,
      accumulator.matSchedules.filter(_ != null).toList,
      accumulator.invalidFights.toSet
    )}.toEither.leftMap(t => Errors.InternalError(t.getMessage))

  private def createMatScheduleContainers(
    mats: List[MatDescriptionDTO],
    periodStartTime: Map[String, Instant],
    timeZone: String
  ) = {
    val initialFightsByMats = mats.zipWithIndex.map { case (mat, i) =>
      val initDate = ZonedDateTime.ofInstant(periodStartTime(mat.getPeriodId), ZoneId.of(timeZone))
      InternalMatScheduleContainer(
        timeZone = timeZone,
        name = mat.getName,
        id = Option(mat.getId).getOrElse(UUID.randomUUID().toString),
        fights = ArrayBuffer.empty,
        currentTime = initDate.toInstant,
        totalFights = 0,
        matOrder = Option(mat.getMatOrder).map(_.toInt).getOrElse(i),
        periodId = mat.getPeriodId
      )
    }
    initialFightsByMats
  }
}
