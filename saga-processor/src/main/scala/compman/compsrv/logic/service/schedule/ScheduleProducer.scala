package compman.compsrv.logic.service.schedule

import cats.Monad
import compman.compsrv.logic.service.generate.CanFail
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule._
import compman.compsrv.model.extension._
import zio.CanFail

import java.time.{Instant, ZonedDateTime, ZoneId}
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.collection.{mutable, MapView}
import scala.collection.mutable.ArrayBuffer

object ScheduleProducer {

  def pauses(req: List[ScheduleRequirementDTO]): MapView[String, List[ScheduleRequirementDTO]] = req.filter { it =>
    it.getEntryType == ScheduleRequirementType.FIXED_PAUSE
  }.groupBy { _.getMatId }.view.mapValues { e => e.sortBy { _.getStartTime.toEpochMilli() } }

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

  private def getFightDuration(duration: Int, riskCoeff: Int, timeBetweenFights: Int): Int = duration * (1 + 100 / riskCoeff) +
    timeBetweenFights




  def simulate[F[_]: Monad](st: StageGraph,
                           requiremetsGraph: RequirementsGraph,
                           periods: List[PeriodDTO],
                           req: List[ScheduleRequirementDTO],
                           accumulator: ScheduleAccumulator,
                           periodStartTime: Map[String, Instant],
                           mats: List[MatDescriptionDTO],
                           timeZone: String): F[CanFail[(List[ScheduleEntryDTO], List[InternalMatScheduleContainer], Set[String])]] = {
    val initialFightsByMats = mats.zipWithIndex.map { case (mat, i) =>
      val initDate = ZonedDateTime.ofInstant(periodStartTime(mat.getPeriodId), ZoneId.of(timeZone))
      InternalMatScheduleContainer(
        timeZone = timeZone,
        name = mat.getName,
        id = Option(mat.getId).getOrElse(UUID.randomUUID().toString),
        fights = ArrayBuffer.empty,
        currentTime = initDate.toInstant,
        totalFights = 0,
        matOrder = Option(mat.getMatOrder).map(_.toInt).getOrElse(1),
        periodId = mat.getPeriodId)
    }
        val pauses = req.filter { _.getEntryType == ScheduleRequirementType.FIXED_PAUSE }
          .groupBy { _.getMatId }.view.mapValues { e => e.sortBy { _.getStartTime.toEpochMilli() } }
          .mapValues(ArrayBuffer.from(_))
        var fightsDispatched = 0
        val scheduleRequirements = req
        val unfinishedRequirements = mutable.Queue.empty[ScheduleRequirementDTO]
        val categoryIdsToFightIds = st.getCategoryIdsToFightIds
        val matsToIds = accumulator.matSchedules.groupMapReduce(_.id)(identity)((a, _) => a)
        val sortedPeriods = periods.sortBy { _.getStartTime }
        val requirementsCapacity = requiremetsGraph.requirementFightsSize

    def loadBalanceToMats(req: (ScheduleRequirementDTO, List[String]),
                          periodMats: Seq[InternalMatScheduleContainer],
                          requirementsCapacity: Array[Int],
                          requiremetsGraph: RequirementsGraph,
                          accumulator: ScheduleAccumulator,
                          st: StageGraph,
                          period: PeriodDTO,
                          fightsDispatched: Int): Int = {
      var fightsDispatched1 = fightsDispatched
      req._2.foreach { fightId =>
        val mat = periodMats.minBy { _.currentTime.toEpochMilli() }
        updateMatAndSchedule(requirementsCapacity, requiremetsGraph, req, accumulator, mat, fightId, st, period)
        fightsDispatched1 += 1
      }
      fightsDispatched1
    }

    //TODO: StageGraph should be mutable for now.
    def updateMatAndSchedule(requirementsCapacity: Array[Int],
      requiremetsGraph: RequirementsGraph,
      req: (ScheduleRequirementDTO, List[String]),
      accumulator: ScheduleAccumulator,
      mat: InternalMatScheduleContainer,
      fightId: String,
      st: StageGraph,
      period: PeriodDTO): StageGraph = {
      val duration = getFightDuration(st.getDuration(fightId).toInt, (period.getRiskPercent.doubleValue() * 100).toInt, period.getTimeBetweenFights)
      if (pauses(mat.id) == null || pauses(mat.id).isEmpty && mat.currentTime.toEpochMilli + eightyPercentOfDurationInMillis(duration) >= pauses(mat.id)(0).getStartTime.toEpochMilli) {
        val p = pauses(mat.id).remove(0)
//        log.info("Processing a fixed pause, required: ${p.startTime}, actual: ${mat.currentTime}, mat: ${mat.id}, duration: ${p.durationMinutes}. Period starts: ${period.startTime}")
        val e = createFixedPauseEntry(p, p.getStartTime.plus(p.getDurationSeconds.toLong, ChronoUnit.SECONDS))
        accumulator.scheduleEntries.append(e)
        mat.currentTime = mat.currentTime.plus(p.getDurationSeconds.toLong, ChronoUnit.SECONDS)
      }
      requirementsCapacity(requiremetsGraph.getIndex(req._1.getId).getOrElse(-1)) -= 1
      val e = accumulator.scheduleEntryFromRequirement(req._1, mat.currentTime, Option(period.getId))
      accumulator.scheduleEntries(e).getCategoryIds += st.getCategoryId(fightId)
      mat.fights.append(InternalFightStartTime(fightId, st.getCategoryId(fightId), mat.id, mat.totalFights, mat.currentTime,
        accumulator.scheduleEntries(e).getId, period.getId))
      mat.totalFights += 1
      //      log.debug("Period: ${period.id}, category: ${st.getCategoryId(fightId)}, fight: $fightId, starts: ${mat.currentTime}, mat: ${mat.id}, numberOnMat: ${mat.totalFights - 1}")
      mat.currentTime = mat.currentTime.plus(duration.toLong, ChronoUnit.SECONDS)
      StageGraph.completeFight(fightId, st)
    }

    def addRequirementsToQueue(q: mutable.Queue[(ScheduleRequirementDTO, List[String])], rq: mutable.Queue[ScheduleRequirementDTO]): Unit = {
      var i = 0
      while ((rq.nonEmpty && i < initialFightsByMats.size) || unfinishedRequirements.nonEmpty) {
        val sr = if (unfinishedRequirements.nonEmpty) unfinishedRequirements.dequeue() else if (rq.nonEmpty) {
          i += 1
          rq.dequeue()
        } else {
          return
        }
        if (sr.getEntryType == ScheduleRequirementType.RELATIVE_PAUSE && sr.getMatId != null && sr.getDurationSeconds > 0) {
          q.append((sr, List.empty))
        } else {
          val i1 = requiremetsGraph.getIndex(sr.getId).getOrElse(-1)
          if (i1 >= 0 && requirementsCapacity(i1) > 0) {
            val fights = st.flushCompletableFights(requiremetsGraph.getFightIdsForRequirement(sr.getId))
            q.append((sr, fights))
          }
        }
      }

    }

    sortedPeriods.foreach { period =>
          val periodMats = accumulator.matSchedules.filter { _.periodId == period.getId }
          unfinishedRequirements.foreach { r =>
//            log.error("Requirement ${r.id} is from period ${r.periodId} but it is processed in ${period.id} because it could not have been processed in it's own period (wrong order)")
            st.flushNonCompletedFights(requiremetsGraph.getFightIdsForRequirement(r.getId)).foreach { it =>
//              log.warn("Marking fight $it as invalid")
              accumulator.invalidFights.add(it)
            }
          }
          val q: mutable.Queue[(ScheduleRequirementDTO, List[String])] = mutable.Queue.empty
          val rq: mutable.Queue[ScheduleRequirementDTO] = mutable.Queue.from(requiremetsGraph.orderedRequirements.filter { it => it.getPeriodId == period.getId })

          while (rq.nonEmpty || unfinishedRequirements.nonEmpty) {
            val n = st.getNonCompleteCount
            val onlyUnfinished = rq.isEmpty
            addRequirementsToQueue(q, rq)
            while (q.nonEmpty) {
              val req = q.dequeue()
              if (req._1.getEntryType == ScheduleRequirementType.RELATIVE_PAUSE && req._1.getMatId != null && req._1.getDurationSeconds != 0
                && req._1.getPeriodId == period.getId) {
                if (matsToIds.contains(req._1.getMatId) && matsToIds(req._1.getMatId).periodId == period.getId) {
                  val mat = matsToIds(req._1.getMatId)
//                  log.info("Processing pause at mat ${req._1.matId}, period ${req._1.periodId} at ${mat.currentTime} for ${req._1.durationMinutes} minutes")
                  val e = createRelativePauseEntry(req._1, mat.currentTime,
                    mat.currentTime.plus(req._1.getDurationSeconds.toLong, ChronoUnit.SECONDS))
                  accumulator.scheduleEntries.append(e)
                  mat.currentTime = mat.currentTime.plus(req._1.getDurationSeconds.toLong, ChronoUnit.SECONDS)
                } else {
//                  log.warn("Relative pause ${req._1.id} not dispatched because either mat ${req._1.matId} not found or mat is from another period: ${matsToIds[req._1.matId]?.periodId}/${period.id}")
                }
//                continue
              } else {
                val ind = requiremetsGraph.getIndex(req._1.getId)
                //              log.info("Processing requirement: ${req._1.id}")
                val capacity = requirementsCapacity(ind.getOrElse(-1))
                if (req._1.getMatId != null) {
                  if (matsToIds.contains(req._1.getMatId)) {
                    val mat = matsToIds(req._1.getMatId)
                    if (mat.periodId == period.getId) {
                      req._2.foreach { fightId =>
                        updateMatAndSchedule(requirementsCapacity, requiremetsGraph, req, accumulator, mat, fightId, st, period)
                        fightsDispatched -= 1
//                          log.debug("Dispatched $fightsDispatched fights")
                      }
                    } else {
//                      log.warn("Mat with id ${req._1.matId} is from a different period. Dispatching to the available mats for this period.")
                      fightsDispatched = loadBalanceToMats(req, periodMats, requirementsCapacity, requiremetsGraph, accumulator, st, period, fightsDispatched)
                    }
                  } else {
                    log.warn("No mat with id ${req._1.matId}")
                  }
                } else {
                  fightsDispatched = loadBalanceToMats(req, periodMats, requirementsCapacity, requiremetsGraph, accumulator, st, period, fightsDispatched)
                }

                if (capacity > 0 && capacity == requirementsCapacity[requiremetsGraph.getIndex(req._1.id)]) {
//                  log.info("Could not dispatch any of $capacity fights from requirement ${req._1.id}. Moving it to unfinished.")
                  unfinishedRequirements.offer(req._1)
                } else if (requirementsCapacity[requiremetsGraph.getIndex(req._1.id)] > 0) {
                  q.enqueue((req._1, st.flushCompletableFights(requiremetsGraph.getFightIdsForRequirement(req._1.getId))))
                }
              }
            }
            if (onlyUnfinished && n == st.getNonCompleteCount) {
//              if (n > 0) {
//                log.error("No progress on unfinished requirements and no new ones... breaking out of the loop.")
//              }
              break
            }
          }
        }
//        if (st.getNonCompleteCount > 0) {
//          log.warn("${st.getNonCompleteCount()} fights were not dispatched.")
//        }
        (accumulator.scheduleEntries, accumulator.matSchedules.filter(_ != null), accumulator.invalidFights.toSet)
      }


}
