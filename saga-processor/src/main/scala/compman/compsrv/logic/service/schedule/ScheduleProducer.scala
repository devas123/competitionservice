package compman.compsrv.logic.service.schedule

import compman.compsrv.model.dto.schedule._

import java.time.Instant
import scala.collection.MapView

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

  private def getFightDuration(duration: Int, riskCoeff: Int, timeBetweenFights: Int): Int = duration * riskCoeff +
    timeBetweenFights

  def simulate(): (List[ScheduleEntryDTO], List[InternalMatScheduleContainer], Set[String]) = {
    return this.stages
      .let { st ->
        var fightsDispatched = 0
        val scheduleRequirements = req
        val unfinishedRequirements = LinkedList<ScheduleRequirementDTO>()
        val accumulator = supplier().get()
        val categoryIdsToFightIds = st.getCategoryIdsToFightIds()
        val matsToIds = accumulator.matSchedules.map { it.id to it }.toMap()
        val sortedPeriods = periods.sortedBy { it.startTime }
        val requiremetsGraph = RequirementsGraph(scheduleRequirements.map { it.id to it }.toMap(), categoryIdsToFightIds, sortedPeriods.map { it.id }.toTypedArray())
        val requirementsCapacity = requiremetsGraph.getRequirementsFightsSize()
        sortedPeriods.forEach { period ->
          val periodMats = accumulator.matSchedules.filter { it.periodId == period.id }
          unfinishedRequirements.forEach { r ->
            log.error("Requirement ${r.id} is from period ${r.periodId} but it is processed in ${period.id} because it could not have been processed in it's own period (wrong order)")
            st.flushNonCompletedFights(requiremetsGraph.getFightIdsForRequirement(r.id)).forEach {
              log.warn("Marking fight $it as invalid")
              accumulator.invalidFights.add(it)
            }
          }
          val q: Queue<Pair<ScheduleRequirementDTO, List<String>>> = LinkedList()
          val rq: Queue<ScheduleRequirementDTO> = LinkedList(requiremetsGraph.orderedRequirements.filter { it.periodId == period.id })

          while (!rq.isEmpty() || !unfinishedRequirements.isEmpty()) {
            val n = st.getNonCompleteCount()
            val onlyUnfinished = rq.isEmpty()
            var i = 0
            while((!rq.isEmpty() && i < initialFightsByMats.size) || !unfinishedRequirements.isEmpty()) {
              val sr= unfinishedRequirements.poll() ?: run {
                i++
                  rq.poll()
              } ?: break
              if (sr.entryType == ScheduleRequirementType.RELATIVE_PAUSE && sr.matId != null && sr.durationMinutes != null) {
                q.add(sr to emptyList())
              } else {
                if (requirementsCapacity[requiremetsGraph.getIndex(sr.id)] > 0) {
                  val fights = st.flushCompletableFights(requiremetsGraph.getFightIdsForRequirement(sr.id))
                  q.add(sr to fights)
                }
              }
            }
            while (!q.isEmpty()) {
              val req = q.poll()
              if (req.first.entryType == ScheduleRequirementType.RELATIVE_PAUSE && !req.first.matId.isNullOrBlank() && req.first.durationMinutes != null
                && req.first.periodId == period.id) {
                if (matsToIds.containsKey(req.first.matId) && matsToIds.getValue(req.first.matId).periodId == period.id) {
                  val mat = matsToIds.getValue(req.first.matId)
                  log.info("Processing pause at mat ${req.first.matId}, period ${req.first.periodId} at ${mat.currentTime} for ${req.first.durationMinutes} minutes")
                  val e = createRelativePauseEntry(req.first, mat.currentTime,
                    mat.currentTime.plus(req.first.durationMinutes.toLong(), ChronoUnit.MINUTES))
                  accumulator.scheduleEntries.add(e)
                  mat.currentTime = mat.currentTime.plus(req.first.durationMinutes.toLong(), ChronoUnit.MINUTES)
                } else {
                  log.warn("Relative pause ${req.first.id} not dispatched because either mat ${req.first.matId} not found or mat is from another period: ${matsToIds[req.first.matId]?.periodId}/${period.id}")
                }
                continue
              }
              log.info("Processing requirement: ${req.first.id}")
              val capacity = requirementsCapacity[requiremetsGraph.getIndex(req.first.id)]
              if (!req.first.matId.isNullOrBlank()) {
                if (matsToIds.containsKey(req.first.matId)) {
                  val mat = matsToIds[req.first.matId]
                  ?: error("No mat with id: ${req.first.matId}")
                  if (mat.periodId == period.id) {
                    req.second.forEach { fightId ->
                      updateMatAndSchedule(requirementsCapacity, requiremetsGraph, req, accumulator, mat, fightId, st, period)
                      fightsDispatched++
                        log.debug("Dispatched $fightsDispatched fights")
                    }
                  } else {
                    log.warn("Mat with id ${req.first.matId} is from a different period. Dispatching to the available mats for this period.")
                    fightsDispatched = loadBalanceToMats(req, periodMats, requirementsCapacity, requiremetsGraph, accumulator, st, period, fightsDispatched)
                  }
                } else {
                  log.warn("No mat with id ${req.first.matId}")
                }
              } else {
                fightsDispatched = loadBalanceToMats(req, periodMats, requirementsCapacity, requiremetsGraph, accumulator, st, period, fightsDispatched)
              }

              if (capacity > 0 && capacity == requirementsCapacity[requiremetsGraph.getIndex(req.first.id)]) {
                log.info("Could not dispatch any of $capacity fights from requirement ${req.first.id}. Moving it to unfinished.")
                unfinishedRequirements.offer(req.first)
              } else if (requirementsCapacity[requiremetsGraph.getIndex(req.first.id)] > 0) {
                q.offer(req.first to st.flushCompletableFights(requiremetsGraph.getFightIdsForRequirement(req.first.id)))
              }
            }
            if (onlyUnfinished && n == st.getNonCompleteCount()) {
              if (n > 0) {
                log.error("No progress on unfinished requirements and no new ones... breaking out of the loop.")
              }
              break
            }
          }
        }
        if (st.getNonCompleteCount() > 0) {
          log.warn("${st.getNonCompleteCount()} fights were not dispatched.")
        }
        Tuple3(accumulator.scheduleEntries, accumulator.matSchedules.filterNotNull(), accumulator.invalidFights.toSet())
      }
  }


}
