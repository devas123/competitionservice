package compman.compsrv.logic.schedule

import cats.implicits._
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.timestamp.Timestamp.toJavaProto
import com.google.protobuf.util.{Durations, Timestamps}
import compman.compsrv.Utils.groupById
import compman.compsrv.logic.fight.CanFail
import compman.compsrv.model.extensions._
import compman.compsrv.model.Errors
import compservice.model.protobuf.model._

import java.time.{Instant, ZonedDateTime, ZoneId}
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

private[schedule] object ScheduleProducer {

  case class ScheduleRequirementWithFightIds(requirement: ScheduleRequirement, fightIds: List[String])
  def toMillis(timestamp: Timestamp): Long = Timestamps.toMillis(toJavaProto(timestamp))
  def plus(timestamp: Timestamp, durationMillis: Long): Timestamp = Timestamp
    .fromJavaProto(Timestamps.add(toJavaProto(timestamp), Durations.fromMillis(durationMillis)))
  def minus(timestamp: Timestamp, durationMillis: Long): Timestamp = Timestamp
    .fromJavaProto(Timestamps.subtract(toJavaProto(timestamp), Durations.fromMillis(durationMillis)))

  private def eightyPercentOfDurationInMillis(durationInSeconds: Int): Int = (durationInSeconds * 8 / 10) * 1000

  private def createPauseEntry(
    pauseReq: ScheduleRequirement,
    startTime: Timestamp,
    endTime: Timestamp,
    pauseType: ScheduleEntryType
  ): ScheduleEntry = {
    ScheduleEntry().withId(pauseReq.id).withCategoryIds(Seq.empty).withFightScheduleInfo(Seq(
      StartTimeInfo().withSomeId(pauseReq.id).withMatId(pauseReq.getMatId).withStartTime(startTime)
    )).withPeriodId(pauseReq.periodId).withStartTime(startTime).withNumberOfFights(0).withEntryType(pauseType)
      .withEndTime(endTime).withDuration(pauseReq.getDurationSeconds).withRequirementIds(Seq(pauseReq.id))
  }

  private def createFixedPauseEntry(fixedPause: ScheduleRequirement, endTime: Timestamp) =
    createPauseEntry(fixedPause, fixedPause.getStartTime, endTime, ScheduleEntryType.FIXED_PAUSE)

  private def createRelativePauseEntry(requirement: ScheduleRequirement, startTime: Timestamp, endTime: Timestamp) =
    createPauseEntry(requirement, startTime, endTime, ScheduleEntryType.RELATIVE_PAUSE)

  private def getFightDuration(duration: Int, riskCoeff: Int, timeBetweenFights: Int): Int = duration *
    (100 + riskCoeff) / 100 + timeBetweenFights

  type ScheduleEntriesWithMatsWithInvalidFights = (List[ScheduleEntry], List[InternalMatScheduleContainer], Set[String])

  def simulate(
    stageGraph: StageGraph,
    requiremetsGraph: RequirementsGraph,
    periods: List[Period],
    req: List[ScheduleRequirement],
    periodStartTime: Map[String, Instant],
    mats: List[MatDescription],
    timeZone: String
  ): CanFail[ScheduleEntriesWithMatsWithInvalidFights] = Try {
    val initialFightsByMats: List[InternalMatScheduleContainer] =
      createMatScheduleContainers(mats, periodStartTime, timeZone)
    val accumulator: ScheduleAccumulator = ScheduleAccumulator(initialFightsByMats)
    val pauses = req.filter { _.entryType == ScheduleRequirementType.FIXED_PAUSE }.groupBy { _.matId }.view
      .mapValues { e => e.sortBy { tt => toMillis(tt.getStartTime) } }.mapValues(ArrayBuffer.from(_))
      .filterKeys(_.isDefined).map(e => e._1.get -> e._2).toMap
    val unfinishedRequirements = mutable.Queue.empty[ScheduleRequirement]
    val matsToIds              = groupById(accumulator.matSchedules)(_.id)
    val sortedPeriods          = periods.sortBy { _.getStartTime }
    val requirementsCapacity   = requiremetsGraph.requirementFightsSize.toArray

    def loadBalanceToMats(
      req: ScheduleRequirementWithFightIds,
      periodMats: Seq[InternalMatScheduleContainer],
      requirementsCapacity: Array[Int],
      requiremetsGraph: RequirementsGraph,
      accumulator: ScheduleAccumulator,
      st: StageGraph,
      period: Period
    ): StageGraph = {
      req.fightIds.foldLeft(st) { (stageGraph, fightId) =>
        val mat = periodMats.minBy { _.currentTime.toEpochMilli() }
        updateMatAndSchedule(requirementsCapacity, requiremetsGraph, req, accumulator, mat, fightId, stageGraph, period)
      }
    }

    def updateMatAndSchedule(
      requirementsCapacity: Array[Int],
      requiremetsGraph: RequirementsGraph,
      req: ScheduleRequirementWithFightIds,
      accumulator: ScheduleAccumulator,
      mat: InternalMatScheduleContainer,
      fightId: String,
      st: StageGraph,
      period: Period
    ): StageGraph = {
      val duration = getFightDuration(st.getDuration(fightId), period.riskPercent, period.timeBetweenFights)
      if (shouldAddPauseToMatBeforeFight(pauses, mat, duration)) {
        val p = pauses(mat.id).remove(0)
        val e = createFixedPauseEntry(
          p,
          p.endTime.getOrElse(plus(p.getStartTime, TimeUnit.SECONDS.toMillis(p.getDurationSeconds.toLong)))
        )
        accumulator.scheduleEntries.append(e)
        mat.currentTime = mat.currentTime.plus(p.getDurationSeconds.toLong, ChronoUnit.SECONDS)
      }
      requirementsCapacity(requiremetsGraph.getIndex(req.requirement.id).getOrElse(-1)) -= 1
      val e = accumulator.scheduleEntryFromRequirement(req.requirement, mat.currentTime, Option(period.id))
      accumulator.scheduleEntries(e) = accumulator.scheduleEntries(e).addCategoryId(st.getCategoryId(fightId))
      mat.fights.append(InternalFightStartTime(
        fightId,
        st.getCategoryId(fightId),
        mat.id,
        mat.totalFights,
        mat.currentTime,
        accumulator.scheduleEntries(e).id,
        period.id
      ))
      mat.totalFights += 1
      mat.currentTime = mat.currentTime.plus(duration.toLong, ChronoUnit.SECONDS)
      StageGraph.completeFight(fightId, st)
    }

    def addRequirementsToQueue(
      queueToUpdate: mutable.Queue[ScheduleRequirementWithFightIds],
      requirementsQueue: mutable.Queue[ScheduleRequirement],
      stgGr: StageGraph
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
          sr.entryType == ScheduleRequirementType.RELATIVE_PAUSE && sr.matId.isDefined && sr.durationSeconds.isDefined
        ) { queueToUpdate.append(ScheduleRequirementWithFightIds(sr, List.empty)) }
        else {
          val i1 = requiremetsGraph.getIndex(sr.id).getOrElse(-1)
          if (i1 >= 0 && requirementsCapacity(i1) > 0) {
            val fights = stgGr.flushCompletableFights(requiremetsGraph.getFightIdsForRequirement(sr.id))
            queueToUpdate.append(ScheduleRequirementWithFightIds(sr, fights))
          }
        }
      }

    }

    def dispatchFightsFromQueue(
      period: Period,
      periodMats: mutable.Seq[InternalMatScheduleContainer],
      q: mutable.Queue[ScheduleRequirementWithFightIds],
      stGr: StageGraph
    ): StageGraph = {
      var sg = stGr
      while (q.nonEmpty) {
        val req = q.dequeue()
        if (isValidRelativePause(period, req)) {
          if (matsToIds.contains(req.requirement.getMatId) && matsToIds(req.requirement.getMatId).periodId == period.id) {
            val mat = matsToIds(req.requirement.getMatId)
            val e = createRelativePauseEntry(
              req.requirement,
              mat.currentTime.asTimestamp,
              mat.currentTime.plus(req.requirement.getDurationSeconds.toLong, ChronoUnit.SECONDS).asTimestamp
            )
            accumulator.scheduleEntries.append(e)
            mat.currentTime = mat.currentTime.plus(req.requirement.getDurationSeconds.toLong, ChronoUnit.SECONDS)
          }
        } else {
          val ind      = requiremetsGraph.getIndex(req.requirement.id)
          val capacity = requirementsCapacity(ind.getOrElse(-1))
          if (req.requirement.matId.isDefined) {
            if (matsToIds.contains(req.requirement.getMatId)) {
              val mat = matsToIds(req.requirement.getMatId)
              if (mat.periodId == period.id) {
                sg = loadBalanceToMats(req, Seq(mat), requirementsCapacity, requiremetsGraph, accumulator, sg, period)
              } else {
                sg = loadBalanceToMats(
                  req,
                  periodMats.toSeq,
                  requirementsCapacity,
                  requiremetsGraph,
                  accumulator,
                  sg,
                  period
                )
              }
            }
          } else {
            sg =
              loadBalanceToMats(req, periodMats.toSeq, requirementsCapacity, requiremetsGraph, accumulator, sg, period)
          }

          if (capacity > 0 && capacity == requirementsCapacity(requiremetsGraph.getIndexOrMinus1(req.requirement.id))) {
            unfinishedRequirements.enqueue(req.requirement)
          } else if (requirementsCapacity(requiremetsGraph.getIndexOrMinus1(req.requirement.id)) > 0) {
            q.enqueue(ScheduleRequirementWithFightIds(req.requirement, sg.flushCompletableFights(requiremetsGraph.getFightIdsForRequirement(req.requirement.id))))
          }
        }
      }
      sg
    }

    var sg = stageGraph
    sortedPeriods.foreach { period =>
      val periodMats = accumulator.matSchedules.filter { _.periodId == period.id }
      unfinishedRequirements.foreach { r =>
        sg.flushNonCompletedFights(requiremetsGraph.getFightIdsForRequirement(r.id)).foreach { it =>
          accumulator.invalidFights.add(it)
        }
      }
      val q: mutable.Queue[ScheduleRequirementWithFightIds] = mutable.Queue.empty
      val rq: mutable.Queue[ScheduleRequirement] = mutable.Queue
        .from(requiremetsGraph.orderedRequirements.filter { it => it.periodId == period.id })
      var dispatchSuccessful = true
      while ((rq.nonEmpty || unfinishedRequirements.nonEmpty) && dispatchSuccessful) {
        val n              = sg.getNonCompleteCount
        val onlyUnfinished = rq.isEmpty
        addRequirementsToQueue(q, rq, sg)
        sg = dispatchFightsFromQueue(period, periodMats, q, sg)
        dispatchSuccessful = !(onlyUnfinished && n == sg.getNonCompleteCount && n == 0)
      }
    }
    (
      accumulator.scheduleEntries.toList,
      accumulator.matSchedules.filter(_ != null).toList,
      accumulator.invalidFights.toSet
    )
  }.toEither.leftMap(t => {
    t.printStackTrace()
    Errors.InternalException(t)
  })

  private def shouldAddPauseToMatBeforeFight(pauses: Map[String, ArrayBuffer[ScheduleRequirement]], mat: InternalMatScheduleContainer, duration: Int) = {
    pauses.get(mat.id).exists(_.nonEmpty) &&
      mat.currentTime.toEpochMilli + eightyPercentOfDurationInMillis(duration) >=
        toMillis(pauses(mat.id)(0).getStartTime)
  }

  private def isValidRelativePause(period: Period, req: ScheduleRequirementWithFightIds) = {
    req.requirement.entryType == ScheduleRequirementType.RELATIVE_PAUSE && req.requirement.matId.isDefined &&
      req.requirement.durationSeconds.isDefined && req.requirement.periodId == period.id
  }

  private def createMatScheduleContainers(
    mats: List[MatDescription],
    periodStartTime: Map[String, Instant],
    timeZone: String
  ) = {
    val initialFightsByMats = mats.zipWithIndex.map { case (mat, i) =>
      val initDate = ZonedDateTime.ofInstant(periodStartTime(mat.periodId), ZoneId.of(timeZone))
      InternalMatScheduleContainer(
        timeZone = timeZone,
        name = mat.name,
        id = Option(mat.id).getOrElse(UUID.randomUUID().toString),
        fights = ArrayBuffer.empty,
        currentTime = initDate.toInstant,
        totalFights = 0,
        matOrder = Option(mat.matOrder).getOrElse(i),
        periodId = mat.periodId
      )
    }
    initialFightsByMats
  }
}
