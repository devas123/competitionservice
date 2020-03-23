package compman.compsrv.service

import arrow.core.Tuple3
import arrow.core.Tuple4
import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import com.compmanager.service.ServiceException
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.service.schedule.BracketSimulatorFactory
import compman.compsrv.service.schedule.IBracketSimulator
import compman.compsrv.util.IDGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

@Component
class ScheduleService(private val bracketSimulatorFactory: BracketSimulatorFactory) {

    companion object {
        fun obsoleteFight(f: FightDescription, threeCompetitorCategory: Boolean = false): Boolean {
            if (threeCompetitorCategory) {
                return false
            }
            if ((f.parent_1FightId != null) || (f.parent_2FightId != null)) return false
            return f.status == FightStatus.UNCOMPLETABLE.ordinal || f.status == FightStatus.WALKOVER.ordinal
        }

        fun getAllFightsParents(f: FightDescription, getFight: (fightId: String) -> FightDescription?): List<String> {
            tailrec fun loop(result: List<String>, parentIds: List<String>): List<String> {
                return if (parentIds.isEmpty()) {
                    result
                } else {
                    loop(result + parentIds, parentIds.flatMap {
                        getFight(it)?.let { pf -> listOfNotNull(pf.parent_1FightId, pf.parent_2FightId) }.orEmpty()
                    })
                }
            }
            return loop(emptyList(), listOfNotNull(f.parent_1FightId, f.parent_2FightId))
        }

        private val log: Logger = LoggerFactory.getLogger(ScheduleService::class.java)
    }

    private data class InternalFightStartTime(val fight: FightDescription,
                                              val matId: String,
                                              val fightNumber: Int,
                                              val startTime: Instant,
                                              val periodId: String)

    private data class InternalMatScheduleContainer(
            val currentTime: Instant,
            val name: String,
            val matOrder: Int?,
            val totalFights: Int,
            val id: String,
            val periodId: String,
            val fights: List<InternalFightStartTime>,
            val timeZone: String,
            val pending: LinkedHashSet<FightDescription>,
            val invalid: LinkedHashSet<FightDescription>)


    private class ScheduleComposer(val startTime: Map<String, Instant>,
                                   val mats: List<MatDescriptionDTO>,
                                   private val scheduleRequirements: List<ScheduleRequirementDTO>,
                                   private val brackets: Flux<IBracketSimulator>,
                                   val timeBetweenFights: Map<String, BigDecimal>,
                                   riskFactor: Map<String, BigDecimal>,
                                   val timeZone: String,
                                   val getFight: (fightId: String) -> FightDescription?) {
        val riskCoeff = riskFactor.mapValues { BigDecimal.ONE.plus(it.value) }

        private fun internalMatById2(fightsByMats: List<InternalMatScheduleContainer>) = { matId: String -> fightsByMats.first { it.id == matId } }

        fun fightNotRegistered(fightId: String, schedule: List<ScheduleEntryDTO>) =
                schedule.none { it.fightIds?.contains(fightId) == true }

        fun findRequirementForFight(f: FightDescription) =
                this.scheduleRequirements
                        .firstOrNull {
                            it.entryType == ScheduleRequirementType.FIGHTS && (it.fightIds?.contains(f.id) == true)
                        }
                        ?: this.scheduleRequirements.firstOrNull {
                            it.entryType == ScheduleRequirementType.CATEGORIES && (it.categoryIds?.contains(f.categoryId) == true)
                        }

        fun updateMatInCollection2(fightsByMats: List<InternalMatScheduleContainer>) = { freshMatCopy: InternalMatScheduleContainer ->
            fightsByMats.map {
                if (it.id == freshMatCopy.id) {
                    freshMatCopy
                } else {
                    it
                }
            }
        }

        fun updateScheduleEntry2(schedule: List<ScheduleEntryDTO>) = { newEntryDTO: ScheduleEntryDTO ->
            if (schedule.none { it.id == newEntryDTO.id }) {
                schedule + newEntryDTO
            } else {
                schedule.map {
                    if (it.id == newEntryDTO.id) {
                        newEntryDTO
                    } else {
                        it
                    }
                }
            }
        }

        fun eightyPercentOfDurationInMillis(duration: BigDecimal): Long {
            return duration.multiply(BigDecimal.valueOf(0.8 * 60000000)).toLong()
        }

        private fun fightsAreDispatchedOrCanBeDispatched(fightIds: List<String>, schedule: List<ScheduleEntryDTO>, currentTime: Instant): Boolean {
            return fightIds.fold(true) { acc, fid ->
                acc && scheduleRequirements.any { sr ->
                    (sr.fightIds?.contains(fid) == true
                            && schedule.any { se -> se.requirementIds?.contains(sr.id) == true }) ||
                            startTime[sr.periodId]?.toEpochMilli()!! <= currentTime.toEpochMilli()
                }
            }
        }

        private fun updateSchedule(f: FightDescription,
                                   schedule: List<ScheduleEntryDTO>,
                                   matContainers: List<InternalMatScheduleContainer>,
                                   pauses: MutableList<ScheduleRequirementDTO>,
                                   lastRun: Boolean): Pair<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>> {
            val updateMatInCollection = updateMatInCollection2(matContainers)
            val updateScheduleEntry = updateScheduleEntry2(schedule)
            val internalMatById = internalMatById2(matContainers)
            if (fightNotRegistered(f.id, schedule)) {
                val requirementForFight = findRequirementForFight(f)
                val entryDTO = when {
                    requirementForFight?.fightIds?.contains(f.id) == true || requirementForFight?.categoryIds?.contains(f.categoryId) == true -> {
                        val e = scheduleEntryFromRequirement(requirementForFight, schedule)
                        log.info("Fight ${f.id} from category ${f.categoryId} has requirements. ${e.id}")
                        e
                    }
                    else -> {
                        log.warn("Neither fight category ${f.categoryId} nor fight itself ${f.id} was dispatched. Placing it to random mat.")
                        val defaultMat = matContainers.find { it.fights.isEmpty() }
                                ?: matContainers.minBy { a -> a.currentTime.toEpochMilli() }!!
                        schedule.firstOrNull { it.categoryIds?.contains(f.categoryId) == true && it.requirementIds.isNullOrEmpty() }
                                ?: emptyScheduleEntry(defaultMat)
                    }
                }
                val defaultMat = matContainers.find { it.fights.isEmpty() && it.periodId == entryDTO.periodId }
                        ?: matContainers.filter { it.periodId == entryDTO.periodId }.minBy { a -> a.currentTime.toEpochMilli() }!!
                val mat = entryDTO.matId?.let { internalMatById(it) } ?: defaultMat
                val allFightParents = getAllFightsParents(f, getFight)
                if (!fightsAreDispatchedOrCanBeDispatched(allFightParents, schedule, mat.currentTime) && !lastRun) {
                    log.warn("Fight $f cannot be dispatched because it's parent fights $allFightParents have incorrect order.")
                    return schedule to updateMatInCollection(mat.copy(pending = LinkedHashSet(mat.pending + f), invalid = LinkedHashSet(mat.invalid + f)))
                }
                if ((requirementForFight != null && !previousRequirementsMet(requirementForFight, schedule))) {
                    return schedule to updateMatInCollection(mat.copy(pending = LinkedHashSet(mat.pending + f)))
                }

                val matsWithTheSameCategory = matContainers
                        .filter {
                            it.periodId == mat.periodId &&
                                    it.fights.isNotEmpty() && it.fights.last().fight.categoryId == f.categoryId
                                    && ((f.round ?: -1) - (it.fights.last().fight.round ?: -1)) in 1..2
                        }
                if (matsWithTheSameCategory.isNotEmpty() && !lastRun) {
                    val matWithTheSameCat = matsWithTheSameCategory.minBy { it.currentTime.toEpochMilli() }!!
                    log.debug("Sending fight ${f.categoryId} -> ${f.round} to pending.")
                    return schedule to updateMatInCollection(matWithTheSameCat.copy(pending = LinkedHashSet(matWithTheSameCat.pending + f)))
                }
                val fixedPause = findFixedPauseForMatAndFight(pauses, mat, f)
                if (fixedPause != null) {
                    pauses.removeIf { it.id == fixedPause.id }
                    log.info("Fixed pause.")
                    val endTime = fixedPause.endTime ?: fixedPause.startTime.plusMillis(fixedPause.durationMinutes.multiply(BigDecimal.valueOf(60000L)).toLong())
                    val pauseEntry = createFixedPauseEntry(fixedPause, endTime)
                    return updateScheduleEntry(pauseEntry) to updateMatInCollection(
                            mat.copy(currentTime = endTime, pending = LinkedHashSet(mat.pending + f)))
                }
                val relativePause = findRelativePauseForMatAndFight(pauses, mat, f, requirementForFight)
                if (relativePause != null) {
                    pauses.removeIf { it.id == relativePause.id }
                    log.info("Relative pause.")
                    val endTime = mat.currentTime.plusMillis(relativePause.durationMinutes.multiply(BigDecimal.valueOf(60000L)).toLong())
                    val pauseEntry = createRelativePauseEntry(relativePause, mat.currentTime, endTime)
                    return updateScheduleEntry(pauseEntry) to updateMatInCollection(
                            mat.copy(currentTime = endTime, pending = LinkedHashSet(mat.pending + f)))
                }
                if (entryDTO.startTime == null || entryDTO.startTime <= mat.currentTime || lastRun) {
                    log.info("Dispatching fight ${f.id} -> ${f.round}. to entry ${entryDTO.id}")
                    val newSchedule = updateScheduleEntry(entryDTO.apply {
                        categoryIds = ((categoryIds ?: emptyArray()) + f.categoryId).distinct().toTypedArray()
                        fightIds = (fightIds ?: emptyArray()) + f.id
                        startTime = startTime ?: mat.currentTime
                        numberOfFights = (numberOfFights ?: 0) + 1
                    })
                    return newSchedule to updateMatInCollection(
                            mat.copy(
                                    currentTime = mat.currentTime.plus(Duration.ofMinutes(getFightDuration(f, mat.periodId).toLong())),
                                    fights = mat.fights +
                                            InternalFightStartTime(
                                                    fight = f,
                                                    fightNumber = mat.totalFights + 1,
                                                    startTime = mat.currentTime,
                                                    matId = mat.id,
                                                    periodId = mat.periodId),
                                    totalFights = mat.totalFights + 1))
                } else {
                    log.info("Fight ${f.id} should be started later. Adding it to pending.")
                    return schedule to updateMatInCollection(mat.copy(pending = LinkedHashSet(mat.pending + f)))
                }
            } else {
                log.warn("Fight $f is already registered. Skipping.")
                return schedule to matContainers
            }
        }

        private fun findFixedPauseForMatAndFight(pauses: MutableList<ScheduleRequirementDTO>, mat: InternalMatScheduleContainer, f: FightDescription): ScheduleRequirementDTO? {
            return pauses.filter { it.entryType == ScheduleRequirementType.FIXED_PAUSE }
                    .find {
                        (it.startTime <= mat.currentTime
                                || it.startTime.toEpochMilli() - mat.currentTime.toEpochMilli() <= eightyPercentOfDurationInMillis(f.duration))
                                && it.matId == mat.id
                                && it.periodId == mat.periodId
                    }
        }

        private fun findRelativePauseForMatAndFight(pauses: MutableList<ScheduleRequirementDTO>,
                                                    mat: InternalMatScheduleContainer,
                                                    f: FightDescription,
                                                    requirementForFight: ScheduleRequirementDTO?): ScheduleRequirementDTO? {
            return requirementForFight?.entryOrder?.let {
                pauses.filter { it.entryType == ScheduleRequirementType.RELATIVE_PAUSE }
                        .find {
                            (it.startTime <= mat.currentTime
                                    || it.startTime.toEpochMilli() - mat.currentTime.toEpochMilli() <= eightyPercentOfDurationInMillis(f.duration))
                                    && it.matId == mat.id
                                    && it.periodId == mat.periodId
                        }
            }
        }

        private fun createPauseEntry(pauseReq: ScheduleRequirementDTO, startTime: Instant, endTime: Instant, pauseType: ScheduleEntryType): ScheduleEntryDTO {
            return                 ScheduleEntryDTO().apply {
                id = pauseReq.id
                this.matId = pauseReq.matId!!
                categoryIds = emptyArray()
                fightIds = emptyArray()
                periodId = pauseReq.periodId
                this.startTime = startTime
                numberOfFights = 0
                entryType = pauseType
                this.endTime = endTime
                duration = pauseReq.durationMinutes
                requirementIds = arrayOf(pauseReq.id)
            }

        }

        private fun createFixedPauseEntry(fixedPause: ScheduleRequirementDTO, endTime: Instant)
                = createPauseEntry(fixedPause, fixedPause.startTime, endTime, ScheduleEntryType.FIXED_PAUSE)

        private fun createRelativePauseEntry(requirement: ScheduleRequirementDTO, startTime: Instant, endTime: Instant)
                = createPauseEntry(requirement, startTime, endTime, ScheduleEntryType.RELATIVE_PAUSE)



        private inline fun prevReqPredicate(requirement: ScheduleRequirementDTO, crossinline matCondition: (s: ScheduleRequirementDTO) -> Boolean) = { it: ScheduleRequirementDTO ->
            it.id != requirement.id &&
                    it.entryOrder < requirement.entryOrder
                    && matCondition(it)
                    && it.periodId == requirement.periodId
        }


        private fun previousRequirementsMet(requirement: ScheduleRequirementDTO, schedule: List<ScheduleEntryDTO>): Boolean {
            val previousRequirementIds = if (requirement.matId.isNullOrBlank()) {
                //This is a requirement without mat
                scheduleRequirements.filter(prevReqPredicate(requirement) { s -> s.matId.isNullOrBlank() }).map { it.id }
            } else {
                //This is a requirement for mat
                scheduleRequirements.filter(prevReqPredicate(requirement) { s -> !s.matId.isNullOrBlank() }).map { it.id }
            }
            return previousRequirementIds.isEmpty() || previousRequirementIds.all { requirementId ->
                schedule.any { it.requirementIds?.contains(requirementId) == true }
            }
        }

        private fun emptyScheduleEntry(defaultMat: InternalMatScheduleContainer): ScheduleEntryDTO {
            return ScheduleEntryDTO()
                    .setId(UUID.randomUUID().toString())
                    .setPeriodId(defaultMat.periodId)
                    .setEntryType(ScheduleEntryType.FIGHTS_GROUP)
                    .setFightIds(emptyArray())
                    .setCategoryIds(emptyArray())
                    .setStartTime(defaultMat.currentTime)
                    .setRequirementIds(emptyArray())
        }

        private fun scheduleEntryFromRequirement(requirement: ScheduleRequirementDTO, schedule: List<ScheduleEntryDTO>): ScheduleEntryDTO {
            return (schedule.firstOrNull { it.requirementIds?.contains(requirement.id) == true }
                    ?: ScheduleEntryDTO()
                            .setId(requirement.id + "-entry")
                            .setPeriodId(requirement.periodId)
                            .setMatId(requirement.matId)
                            .setEntryType(ScheduleEntryType.FIGHTS_GROUP)
                            .setFightIds(emptyArray())
                            .setCategoryIds(emptyArray())
                            .setStartTime(requirement.startTime)
                            .setEndTime(requirement.endTime)
                            .setRequirementIds(arrayOf(requirement.id)))
        }

        fun acceptFight(schedule: List<ScheduleEntryDTO>, fightsByMats: List<InternalMatScheduleContainer>, f: FightDescription, pauses: MutableList<ScheduleRequirementDTO>, lastrun: Boolean): Pair<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>> {
            return this.updateSchedule(f, schedule, fightsByMats, pauses, lastrun)
        }

        fun getFightDuration(fight: FightDescription, periodId: String) = fight.duration!!.multiply(riskCoeff[periodId]
                ?: error("No risk coeff for $periodId")).plus(timeBetweenFights[periodId]
                ?: error("No TimeBetweenFights for $periodId"))

        fun simulate(): Mono<Tuple3<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>, Set<String>>> {
            val initialFightsByMats = mats.mapIndexed { i, mat ->
                val initDate = ZonedDateTime.ofInstant(startTime[mat.periodId]
                        ?: error("No Start time for period ${mat.periodId}"), ZoneId.of(timeZone))
                InternalMatScheduleContainer(
                        timeZone = timeZone,
                        name = mat.name,
                        id = mat.id ?: IDGenerator.createMatId(mat.periodId, i),
                        fights = emptyList(),
                        currentTime = initDate.toInstant(),
                        totalFights = 0,
                        pending = LinkedHashSet(),
                        matOrder = mat.matOrder,
                        periodId = mat.periodId, invalid = LinkedHashSet())
            }.toMutableList()
            val pauses = CopyOnWriteArrayList(scheduleRequirements
                    .filter { listOf(ScheduleRequirementType.FIXED_PAUSE, ScheduleRequirementType.RELATIVE_PAUSE).contains(it.entryType) })

            fun <T> MutableList<T>.replaceAll(elements: Collection<T>) {
                this.clear()
                this.addAll(elements)
            }

            return this.brackets.buffer(initialFightsByMats.size + 1 /* на всякий :) */)
                    .collect(Collector.of(
                            Supplier<Tuple4<MutableList<ScheduleEntryDTO>,
                                    MutableList<InternalMatScheduleContainer>,
                                    MutableList<FightDescription>,
                                    MutableSet<String>>> {
                                log.info("Supplier.")
                                Tuple4(mutableListOf(), initialFightsByMats, mutableListOf(), mutableSetOf())
                            },
                            BiConsumer<Tuple4<MutableList<ScheduleEntryDTO>,
                                    MutableList<InternalMatScheduleContainer>,
                                    MutableList<FightDescription>,
                                    MutableSet<String>>, List<IBracketSimulator>> { fightsByMats, brackets ->
                                val br = brackets.toMutableList()
                                log.info("Consumer.")
                                val activeBrackets = ArrayList<IBracketSimulator>()
                                var pendingFights = fightsByMats.c.toList()
                                var sfbm = fightsByMats.a.toList() to fightsByMats.b.toList()
                                while (br.isNotEmpty() || activeBrackets.isNotEmpty()) {
                                    log.info("Loop. ${br.size}, ${activeBrackets.size}")
                                    val fights = ArrayList<FightDescription>()
                                    var i = 0
                                    if (activeBrackets.size >= i + 1) {
                                        fights.addAll(activeBrackets[i++].getNextRound())
                                    }
                                    while (fights.size <= mats.size && br.isNotEmpty()) {
                                        if (activeBrackets.getOrNull(i) == null) {
                                            activeBrackets.add(br.removeAt(0))
                                        }
                                        fights.addAll(activeBrackets[i++].getNextRound())
                                    }
                                    activeBrackets.removeIf { b -> b.isEmpty() }
                                    sfbm = (pendingFights + fights).fold(sfbm) { acc, f ->
                                        this.acceptFight(acc.first, acc.second, f, pauses, false)
                                    }
                                    pendingFights = sfbm.second.flatMap { it.pending }
                                    sfbm = sfbm.copy(second = sfbm.second.map { it.copy(pending = LinkedHashSet()) })
                                }
                                val invalidFights = sfbm.second.flatMap { internalMatScheduleContainer ->
                                    internalMatScheduleContainer.invalid.map { it.id } }
                                fightsByMats.a.replaceAll(sfbm.first)
                                fightsByMats.b.replaceAll(sfbm.second)
                                fightsByMats.c.replaceAll(pendingFights)
                                fightsByMats.d.addAll(invalidFights)
                            },
                            BinaryOperator { t, u ->
                                log.info("Combiner.")
                                val b = t.b.map { mat ->
                                    u.b.find { m -> m.id == mat.id }
                                            ?.let { scheduleContainer ->
                                                val newFights = (mat.fights + scheduleContainer.fights.filter { f -> mat.fights.none { it.fight.id == f.fight.id } })
                                                        .sortedBy { fightStartTime -> fightStartTime.startTime }
                                                        .mapIndexed { ind, f ->
                                                            f.copy(fightNumber = ind + 1)
                                                        }
                                                mat.copy(fights = newFights, totalFights = newFights.size)
                                            } ?: mat
                                }.toMutableList()
                                val a = (t.a + u.a).groupBy { it.id }.mapValues { e ->
                                    //Categories, fights, requirements
                                    val cfr = e.value.fold(Tuple3(emptyList<String>(), emptyList<String>(), emptyList<String>())) { acc, schedEntry ->
                                        Tuple3(acc.a + (schedEntry.categoryIds
                                                ?: emptyArray()), acc.b + (schedEntry.fightIds
                                                ?: emptyArray()), acc.c + (schedEntry.requirementIds ?: emptyArray()))
                                    }
                                    val entry = e.value[0]
                                    entry
                                            .setCategoryIds(cfr.a.toTypedArray())
                                            .setFightIds(cfr.b.toTypedArray())
                                            .setRequirementIds(cfr.c.toTypedArray())
                                }.toList().map { it.second }.toMutableList()
                                Tuple4(a,
                                        b,
                                        (t.c + u.c).distinctBy { it.id }.toMutableList(),
                                        (t.d + u.d).toMutableSet())
                            },
                            Function<Tuple4<MutableList<ScheduleEntryDTO>,
                                    MutableList<InternalMatScheduleContainer>,
                                    MutableList<FightDescription>,
                                    MutableSet<String>>,
                                    Tuple4<MutableList<ScheduleEntryDTO>,
                                            MutableList<InternalMatScheduleContainer>,
                                            MutableList<FightDescription>,
                                            MutableSet<String>>> {
                                log.info("Finisher.")
                                var k = it.c.fold(it.a.toList() to it.b.toList()) { acc, f -> this.acceptFight(acc.first, acc.second, f, pauses, true) }
                                while (!k.second.flatMap { container -> container.pending }.isNullOrEmpty()) {
                                    k = k.second.flatMap { container -> container.pending }
                                            .fold(k.first to k.second.map { scheduleContainer ->
                                                scheduleContainer
                                                        .copy(pending = LinkedHashSet())
                                            }) { acc, f ->
                                                this.acceptFight(acc.first, acc.second, f, pauses, true)
                                            }
                                }
                                Tuple4(k.first.toMutableList(), k.second.toMutableList(), mutableListOf(), it.d)
                            },
                            Collector.Characteristics.IDENTITY_FINISH))
                    .map { Tuple3(it.a, it.b, it.d) }
        }
    }

    /**
     * @param stages - Flux<pair<Tuple3<StageId, CategoryId, BracketType>, fights>>
     */
    fun generateSchedule(competitionId: String, periods: List<PeriodDTO>, stages: Flux<Pair<Tuple3<String, String, BracketType>, List<FightDescription>>>, timeZone: String,
                         categoryCompetitorNumbers: Map<String, Int>, getFight: (fightId: String) -> FightDescription?): ScheduleDTO {
        if (!periods.isNullOrEmpty()) {
            return doGenerateSchedule(competitionId, stages, periods, timeZone, categoryCompetitorNumbers, getFight)
        } else {
            throw ServiceException("Periods are not specified!")
        }
    }

    private fun doGenerateSchedule(competitionId: String,
                                   stages: Flux<Pair<Tuple3<String, String, BracketType>, List<FightDescription>>>,
                                   periods: List<PeriodDTO>,
                                   timeZone: String,
                                   categoryCompetitorNumbers: Map<String, Int>,
                                   getFight: (fightId: String) -> FightDescription?): ScheduleDTO {
        val periodsWithIds = periods.map { periodDTO ->
            val id = periodDTO.id ?: IDGenerator.createPeriodId(competitionId)
            periodDTO.setId(id)
        }

        val enrichedScheduleRequirements = periodsWithIds.flatMap { periodDTO ->
            // we need to process all schedule requirements to explicitly contain all the fight ids here.
            // if we have two schedule groups with specified fights for one category and they do not contain all the fights,
            // we will dynamically create a 'default' schedule group for the remaining fights
            // later if we move the schedule groups we will have to re-calculate the whole schedule again to avoid inconsistencies.
            periodDTO.scheduleRequirements.groupBy { it.matId ?: "${periodDTO.id}_no_mat" }
                    .mapValues { e -> e.value.mapIndexed { ind, v -> v.setEntryOrder(ind) } }
                    .toList()
                    .flatMap { it.second }
                    .mapIndexed { index, it ->
                        it.setEntryOrder(index).setId(it.id
                                ?: IDGenerator.scheduleRequirementId(competitionId, periodDTO.id, index, it.entryType)).setPeriodId(periodDTO.id)
                    }
        }
        val flatFights = enrichedScheduleRequirements.flatMap { it.fightIds?.toList().orEmpty() }
        assert(flatFights.distinct().size == flatFights.size)
        val brackets = stages.filter { categoryCompetitorNumbers[it.first.b] ?: 0 > 0 }
                .map { p ->
                    val tuple3 = p.first
                    bracketSimulatorFactory.createSimulator(tuple3.a, tuple3.b, p.second,
                            tuple3.c, categoryCompetitorNumbers[tuple3.a] ?: 0)
                }

        val composer = ScheduleComposer(startTime = periods.map { p -> p.id!! to p.startTime!! }.toMap(),
                mats = periods.flatMap { it.mats?.toList().orEmpty() },
                scheduleRequirements = enrichedScheduleRequirements,
                brackets = brackets,
                timeBetweenFights = periods.map { p -> p.id!! to BigDecimal(p.timeBetweenFights) }.toMap(),
                riskFactor = periods.map { p -> p.id!! to p.riskPercent }.toMap(),
                timeZone = timeZone,
                getFight = getFight)

        val fightsByMats = composer.simulate().block(Duration.ofMillis(500)) ?: error("Generated schedule is null")
        val invalidFightIds = fightsByMats.c

        return ScheduleDTO()
                .setId(competitionId)
                .setPeriods(periods.mapNotNull { period ->
                    PeriodDTO()
                            .setId(period.id)
                            .setRiskPercent(period.riskPercent)
                            .setTimeBetweenFights(period.timeBetweenFights)
                            .setIsActive(period.isActive)
                            .setScheduleRequirements(period.scheduleRequirements)
                            .setScheduleEntries(fightsByMats.a.filter { it.periodId == period.id }
                                    .sortedBy { it.startTime.toEpochMilli() }
                                    .mapIndexed { i, scheduleEntryDTO ->
                                        scheduleEntryDTO
                                                .setId(IDGenerator
                                                        .scheduleEntryId(competitionId, period.id, i, scheduleEntryDTO.entryType))
                                                .setOrder(i)
                                                .setInvalidFightIds(scheduleEntryDTO.fightIds?.filter { invalidFightIds.contains(it) }?.toTypedArray())
                                    }.toTypedArray())
                            .setMats(fightsByMats.b.filter { it.periodId == period.id }.mapIndexed { i, container ->
                                MatDescriptionDTO()
                                        .setId(container.id)
                                        .setPeriodId(container.periodId)
                                        .setName(container.name)
                                        .setMatOrder(container.matOrder ?: i)
                                        .setFightStartTimes(container.fights.map {
                                            FightStartTimePairDTO()
                                                    .setStartTime(it.startTime)
                                                    .setNumberOnMat(it.fightNumber)
                                                    .setFightId(it.fight.id)
                                                    .setPeriodId(it.periodId)
                                                    .setFightCategoryId(it.fight.categoryId)
                                                    .setMatId(it.matId)
                                        }.toTypedArray())
                            }.toTypedArray())
                            .setStartTime(period.startTime)
                            .setName(period.name)
                }.toTypedArray())
    }
}