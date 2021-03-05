package compman.compsrv.service.schedule

import arrow.core.Tuple3
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.service.schedule.internal.InternalFightStartTime
import compman.compsrv.service.schedule.internal.InternalMatScheduleContainer
import compman.compsrv.service.schedule.internal.ScheduleAccumulator
import compman.compsrv.util.IDGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.function.Supplier

class ScheduleProducer(val competitionId: String,
                       val startTime: Map<String, Instant>,
                       val mats: List<MatDescriptionDTO>,
                       private val req: List<ScheduleRequirementDTO>,
                       private val stages: StageGraph,
                       private val periods: List<PeriodDTO>,
                       val timeBetweenFights: Map<String, BigDecimal>,
                       riskFactor: Map<String, BigDecimal>,
                       val timeZone: String) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ScheduleProducer::class.java)
    }

    private val pauses = req.filter { it.entryType == ScheduleRequirementType.FIXED_PAUSE }.groupBy { it.matId }.mapValues { e -> e.value.sortedBy { it.startTime.toEpochMilli() }.toMutableList() }

    private val riskCoeff = riskFactor.mapValues { BigDecimal.ONE.plus(it.value) }

    private fun eightyPercentOfDurationInMillis(duration: BigDecimal): Long {
        return duration.multiply(BigDecimal.valueOf(0.8 * 60000)).toLong()
    }


    private fun createPauseEntry(pauseReq: ScheduleRequirementDTO, startTime: Instant, endTime: Instant, pauseType: ScheduleEntryType): ScheduleEntryDTO {
        return ScheduleEntryDTO().apply {
            id = pauseReq.id
            categoryIds = emptyArray()
            fightIds = arrayOf(MatIdAndSomeId().setSomeId(pauseReq.id).setMatId(pauseReq.matId!!).setStartTime(startTime))
            periodId = pauseReq.periodId
            this.startTime = startTime
            numberOfFights = 0
            entryType = pauseType
            this.endTime = endTime
            duration = pauseReq.durationMinutes
            requirementIds = arrayOf(pauseReq.id)
        }
    }

    private fun createFixedPauseEntry(fixedPause: ScheduleRequirementDTO, endTime: Instant) = createPauseEntry(fixedPause, fixedPause.startTime, endTime, ScheduleEntryType.FIXED_PAUSE)

    private fun createRelativePauseEntry(requirement: ScheduleRequirementDTO, startTime: Instant, endTime: Instant) = createPauseEntry(requirement, startTime, endTime, ScheduleEntryType.RELATIVE_PAUSE)



    private fun getFightDuration(duration: BigDecimal, periodId: String) = duration.multiply(riskCoeff[periodId]
            ?: error("No risk coeff for $periodId")).plus(timeBetweenFights[periodId] ?: BigDecimal.ZERO)

    fun simulate(): Tuple3<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>, Set<String>> {
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

    private fun loadBalanceToMats(req: Pair<ScheduleRequirementDTO, List<String>>, periodMats: List<InternalMatScheduleContainer>, requirementsCapacity: IntArray, requiremetsGraph: RequirementsGraph, accumulator: ScheduleAccumulator, st: StageGraph, period: PeriodDTO, fightsDispatched: Int): Int {
        var fightsDispatched1 = fightsDispatched
        req.second.forEach { fightId ->
            val mat = periodMats.minByOrNull { it.currentTime.toEpochMilli() }!!
            updateMatAndSchedule(requirementsCapacity, requiremetsGraph, req, accumulator, mat, fightId, st, period)
            fightsDispatched1++
            log.debug("Dispatched $fightsDispatched1 fights")
        }
        return fightsDispatched1
    }

    private fun updateMatAndSchedule(requirementsCapacity: IntArray,
                                     requiremetsGraph: RequirementsGraph,
                                     req: Pair<ScheduleRequirementDTO, List<String>>,
                                     accumulator: ScheduleAccumulator,
                                     mat: InternalMatScheduleContainer,
                                     fightId: String,
                                     st: StageGraph,
                                     period: PeriodDTO) {
        val duration = getFightDuration(st.getDuration(fightId), period.id)
        if (!pauses[mat.id].isNullOrEmpty() && mat.currentTime.toEpochMilli() + eightyPercentOfDurationInMillis(duration) >= pauses.getValue(mat.id)[0].startTime.toEpochMilli()) {
            val p = pauses.getValue(mat.id).removeAt(0)
            log.info("Processing a fixed pause, required: ${p.startTime}, actual: ${mat.currentTime}, mat: ${mat.id}, duration: ${p.durationMinutes}. Period starts: ${period.startTime}")
            val e = createFixedPauseEntry(p, p.startTime.plus(p.durationMinutes.toLong(), ChronoUnit.MINUTES))
            accumulator.scheduleEntries.add(e)
            mat.currentTime = mat.currentTime.plus(p.durationMinutes.toLong(), ChronoUnit.MINUTES)
        }
        requirementsCapacity[requiremetsGraph.getIndex(req.first.id)]--
        val e = accumulator.scheduleEntryFromRequirement(req.first, mat.currentTime, period.id)
        accumulator.scheduleEntries[e].fightIds += MatIdAndSomeId(mat.id, mat.currentTime, fightId)
        accumulator.scheduleEntries[e].categoryIds += st.getCategoryId(fightId)
        mat.fights.add(InternalFightStartTime(fightId, st.getCategoryId(fightId), mat.id, mat.totalFights++, mat.currentTime, accumulator.scheduleEntries[e].id, period.id))
        log.info("Period: ${period.id}, category: ${st.getCategoryId(fightId)}, fight: $fightId, starts: ${mat.currentTime}, mat: ${mat.id}, numberOnMat: ${mat.totalFights - 1}")
        mat.currentTime = mat.currentTime.plus(duration.toLong(), ChronoUnit.MINUTES)
        st.completeFight(fightId)
    }

    private val initialFightsByMats = mats.mapIndexed { i, mat ->
        val initDate = ZonedDateTime.ofInstant(startTime[mat.periodId]
                ?: error("No Start time for period ${mat.periodId}"), ZoneId.of(timeZone))
        InternalMatScheduleContainer(
                timeZone = timeZone,
                name = mat.name,
                id = mat.id ?: IDGenerator.createMatId(mat.periodId),
                fights = mutableListOf(),
                currentTime = initDate.toInstant(),
                totalFights = 0,
                matOrder = mat.matOrder ?: i,
                periodId = mat.periodId)
    }

    private fun supplier(): Supplier<ScheduleAccumulator> {
        return Supplier {
            log.info("Supplier.")
            ScheduleAccumulator(initialFightsByMats, competitionId)
        }
    }
}