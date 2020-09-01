package compman.compsrv.service.schedule

import arrow.core.Tuple3
import arrow.core.Tuple4
import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.repository.collectors.ScheduleEntryAccumulator
import compman.compsrv.service.schedule.internal.InternalFightStartTime
import compman.compsrv.service.schedule.internal.InternalMatScheduleContainer
import compman.compsrv.service.schedule.internal.ScheduleAccumulator
import compman.compsrv.util.IDGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Supplier
import java.util.stream.Collector
import kotlin.collections.ArrayList

class ScheduleProducer(val competitionId: String,
                       val startTime: Map<String, Instant>,
                       val mats: List<MatDescriptionDTO>,
                       private val req: List<ScheduleRequirementDTO>,
                       private val stages: Mono<StageGraph>,
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
            fightIds = arrayOf(MatIdAndSomeId().setSomeId(pauseReq.id).setMatId(pauseReq.matId!!))
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

    fun simulate(): Mono<Tuple3<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>, Set<String>>> {
        return this.stages
                .map { st ->
                    val scheduleRequirements = req
                    val unfinishedRequirements = LinkedList<ScheduleRequirementDTO>()
                    val accumulator = supplier().get()
                    val categoryIdsToFightIds = st.getCategoryIdsToFightIds()
                    val matsToIds = accumulator.matSchedules.map { it.id to it }.toMap()
                    periods.forEach { period ->
                        val requiremetsGraph = RequirementsGraph(scheduleRequirements.filter { it.periodId == period.id }.map { it.id to it }.toMap(), categoryIdsToFightIds)
                        val q: Queue<Pair<ScheduleRequirementDTO, List<String>>> = LinkedList()
                        val requirementsCapacity = requiremetsGraph.getRequirementsFightsSize()
                        val rq: Queue<ScheduleRequirementDTO> = LinkedList(requiremetsGraph.orderedRequirements)
                        while (!rq.isEmpty() || !unfinishedRequirements.isEmpty()) {
                            val united = ArrayList<ScheduleRequirementDTO>(unfinishedRequirements)
                            while (united.size - unfinishedRequirements.size < initialFightsByMats.size && !rq.isEmpty()) {
                                united.add(rq.poll())
                            }
                            while (true) {
                                united.forEach { sr ->
                                    if (sr.entryType == ScheduleRequirementType.RELATIVE_PAUSE && sr.matId != null) {
                                        val mat = matsToIds.getValue(sr.matId)
                                        val e = createRelativePauseEntry(sr, mat.currentTime,
                                                mat.currentTime.plus(sr.durationMinutes.toLong(), ChronoUnit.MINUTES))
                                        accumulator.scheduleEntries.add(ScheduleEntryAccumulator(e))
                                    } else {
                                        val fights = st.flushCompletableFights(requiremetsGraph.getFightIdsForRequirement(sr.id))
                                        if (!fights.isNullOrEmpty()) {
                                            q.add(sr to fights)
                                        }
                                    }
                                }
                                if (q.isEmpty()) {
                                    unfinishedRequirements.clear()
                                    united.forEach { sr ->
                                        if (requirementsCapacity[requiremetsGraph.getIndex(sr.id)] > 0) {
                                            unfinishedRequirements.add(sr)
                                        }
                                    }
                                    break
                                }
                                while (!q.isEmpty()) {
                                    val req = q.poll()
                                    log.info("Processing requirement: ${req.first.id}")
                                    if (!req.first.matId.isNullOrBlank()) {
                                        val mat = matsToIds.getValue(req.first.matId)
                                        req.second.forEach {fightId ->
                                            updateMatAndSchedule(requirementsCapacity, requiremetsGraph, req, accumulator, mat, fightId, st, period)
                                        }
                                    } else {
                                        req.second.forEach {fightId ->
                                            val mat = accumulator.matSchedules.minBy { it.currentTime.toEpochMilli() }!!
                                            updateMatAndSchedule(requirementsCapacity, requiremetsGraph, req, accumulator, mat, fightId, st, period)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Tuple3(accumulator.scheduleEntries.map { it.getScheduleEntry() }, accumulator.matSchedules.filterNotNull(), accumulator.invalidFights.toSet())
                }
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
            val e = createFixedPauseEntry(p, p.startTime.plus(p.durationMinutes.toLong(), ChronoUnit.MINUTES))
            accumulator.scheduleEntries.add(ScheduleEntryAccumulator(e))
            mat.currentTime = mat.currentTime.plus(p.durationMinutes.toLong(), ChronoUnit.MINUTES)
        }
        requirementsCapacity[requiremetsGraph.getIndex(req.first.id)]--
        val e = accumulator.scheduleEntryFromRequirement(req.first, mat.currentTime)
        accumulator.scheduleEntries[e].fightIds.add(MatIdAndSomeId(mat.id, fightId))
        accumulator.scheduleEntries[e].categoryIds.add(st.getCategoryId(fightId))
        mat.fights.add(InternalFightStartTime(fightId, st.getCategoryId(fightId), mat.id, mat.totalFights++, mat.currentTime, accumulator.scheduleEntries[e].getId(), period.id))
        mat.currentTime = mat.currentTime.plus(duration.multiply(BigDecimal.valueOf(60L)).toLong(), ChronoUnit.SECONDS)
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

    fun supplier(): Supplier<ScheduleAccumulator> {
        return Supplier {
            log.info("Supplier.")
            ScheduleAccumulator(initialFightsByMats, competitionId)
        }
    }
}