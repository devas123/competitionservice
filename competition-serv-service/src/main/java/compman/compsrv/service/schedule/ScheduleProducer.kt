package compman.compsrv.service.schedule

import arrow.core.Tuple3
import arrow.core.Tuple4
import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.util.IDGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
import java.util.function.Supplier
import java.util.stream.Collector
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet

data class InternalFightStartTime(val fight: FightDescription,
                                  val matId: String,
                                  val fightNumber: Int,
                                  val startTime: Instant,
                                  val periodId: String)

data class FightDescriptionAndEntryOrder(val f: FightDescription, val entryOrder: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FightDescriptionAndEntryOrder

        if (f.id != other.f.id) return false

        return true
    }

    override fun hashCode(): Int {
        return f.id.hashCode()
    }
}

fun FightDescription.withOrder(order: Int?) = FightDescriptionAndEntryOrder(this, order ?: Int.MAX_VALUE)

data class InternalMatScheduleContainer(
        val currentTime: Instant,
        val name: String,
        val matOrder: Int?,
        val totalFights: Int,
        val id: String,
        val periodId: String,
        val fights: List<InternalFightStartTime>,
        val timeZone: String,
        val pending: LinkedHashSet<FightDescriptionAndEntryOrder>,
        val invalid: LinkedHashSet<FightDescription>)

class ScheduleAccumulator(initialMatSchedules: List<InternalMatScheduleContainer>) {
    val scheduleEntries = mutableListOf<ScheduleEntryDTO>()
    val matSchedules = mutableListOf(*initialMatSchedules.toTypedArray())
    val pendingFights = mutableListOf<FightDescriptionAndEntryOrder>()
    val invalidFights = mutableSetOf<String>()
    val finishedStages = mutableSetOf<String>()
}


class ScheduleProducer(val startTime: Map<String, Instant>,
                       val mats: List<MatDescriptionDTO>,
                       req: List<ScheduleRequirementDTO>,
                       private val brackets: Flux<StageGraph>,
                       val timeBetweenFights: Map<String, BigDecimal>,
                       riskFactor: Map<String, BigDecimal>,
                       val timeZone: String,
                       val getFight: (fightId: String) -> FightDescription?) : Collector<List<IBracketSimulator>, ScheduleAccumulator, Tuple4<List<ScheduleEntryDTO>,
        List<InternalMatScheduleContainer>,
        List<FightDescription>,
        Set<String>>> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ScheduleProducer::class.java)
    }

    private val pauses = CopyOnWriteArrayList(req
            .filter { listOf(ScheduleRequirementType.FIXED_PAUSE, ScheduleRequirementType.RELATIVE_PAUSE).contains(it.entryType) })

    private val scheduleRequirements = CopyOnWriteArrayList(req
            .filter { !listOf(ScheduleRequirementType.FIXED_PAUSE, ScheduleRequirementType.RELATIVE_PAUSE).contains(it.entryType) })


    private val riskCoeff = riskFactor.mapValues { BigDecimal.ONE.plus(it.value) }

    private fun internalMatById2(fightsByMats: List<InternalMatScheduleContainer>) = { matId: String -> fightsByMats.first { it.id == matId } }

    private fun fightNotRegistered(fightId: String, schedule: List<ScheduleEntryDTO>) =
            schedule.none { entry -> entry.fightIds?.any { it.someId == fightId } == true }

    private fun findRequirementForFight(f: FightDescription) =
            this.scheduleRequirements
                    .firstOrNull {
                        it.entryType == ScheduleRequirementType.FIGHTS && (it.fightIds?.contains(f.id) == true)
                    }
                    ?: this.scheduleRequirements.firstOrNull {
                        it.entryType == ScheduleRequirementType.CATEGORIES && (it.categoryIds?.contains(f.categoryId) == true)
                    }

    private fun updateMatInCollection2(fightsByMats: List<InternalMatScheduleContainer>) = { freshMatCopy: InternalMatScheduleContainer ->
        fightsByMats.map {
            if (it.id == freshMatCopy.id) {
                freshMatCopy
            } else {
                it
            }
        }
    }

    private fun updateScheduleEntry2(schedule: List<ScheduleEntryDTO>) = { newEntryDTO: ScheduleEntryDTO ->
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

    private fun eightyPercentOfDurationInMillis(duration: BigDecimal): Long {
        return duration.multiply(BigDecimal.valueOf(0.8 * 60000)).toLong()
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
            val periodId = requirementForFight?.periodId
            val defaultMat = matContainers.find { it.fights.isEmpty() && (periodId.isNullOrBlank() || it.periodId == periodId) }
                    ?: matContainers.filter { periodId.isNullOrBlank() || it.periodId == periodId }
                            .minBy { a -> a.currentTime.toEpochMilli() }!!
            val entryDTO = when {
                requirementForFight?.fightIds?.contains(f.id) == true || requirementForFight?.categoryIds?.contains(f.categoryId) == true -> {
                    val e = scheduleEntryFromRequirement(requirementForFight, schedule)
                    log.info("Fight ${f.id} from category ${f.categoryId} has requirements. ${e.id}")
                    e
                }
                else -> {
                    log.warn("Neither fight category ${f.categoryId} nor fight itself ${f.id} was dispatched. Placing it to random mat.")
                    schedule.firstOrNull { it.categoryIds?.contains(f.categoryId) == true && it.requirementIds.isNullOrEmpty() }
                            ?: emptyScheduleEntry(defaultMat)
                }
            }
            val mat = requirementForFight?.matId?.let { internalMatById(it) } ?: defaultMat
            val allFightParents = ScheduleService.getAllFightsParents(f, getFight)
            if (!fightsAreDispatchedOrCanBeDispatched(allFightParents, schedule, mat.currentTime) && !lastRun) {
                log.warn("Fight $f cannot be dispatched because it's parent fights $allFightParents have incorrect order.")
                return schedule to updateMatInCollection(mat.copy(pending = LinkedHashSet(mat.pending + f.withOrder(requirementForFight?.entryOrder)), invalid = LinkedHashSet(mat.invalid + f)))
            }
            if ((requirementForFight != null && !previousRequirementsMet(requirementForFight, schedule))) {
                return schedule to updateMatInCollection(mat.copy(pending = LinkedHashSet(mat.pending + f.withOrder(requirementForFight.entryOrder))))
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
                return schedule to updateMatInCollection(matWithTheSameCat.copy(pending = LinkedHashSet(matWithTheSameCat.pending + f.withOrder(requirementForFight?.entryOrder))))
            }
            val fixedPause = findFixedPauseForMatAndFight(pauses, mat, f)
            if (fixedPause != null) {
                pauses.removeIf { it.id == fixedPause.id }
                log.info("Fixed pause.")
                val endTime = fixedPause.endTime
                        ?: fixedPause.startTime.plusMillis(fixedPause.durationMinutes.multiply(BigDecimal.valueOf(60000L)).toLong())
                val pauseEntry = createFixedPauseEntry(fixedPause, endTime)
                return updateScheduleEntry(pauseEntry) to updateMatInCollection(
                        mat.copy(currentTime = endTime, pending = LinkedHashSet(mat.pending + f.withOrder(requirementForFight?.entryOrder))))
            }
            val relativePause = findRelativePauseForMatAndFight(pauses, mat, f, requirementForFight)
            if (relativePause != null) {
                pauses.removeIf { it.id == relativePause.id }
                log.info("Relative pause.")
                val endTime = mat.currentTime.plusMillis(relativePause.durationMinutes.multiply(BigDecimal.valueOf(60000L)).toLong())
                val pauseEntry = createRelativePauseEntry(relativePause, mat.currentTime, endTime)
                return updateScheduleEntry(pauseEntry) to updateMatInCollection(
                        mat.copy(currentTime = endTime, pending = LinkedHashSet(mat.pending + f.withOrder(requirementForFight?.entryOrder))))
            }
            if (entryDTO.startTime == null || entryDTO.startTime <= mat.currentTime || lastRun) {
                log.info("Dispatching fight ${f.id} -> ${f.round}. to entry ${entryDTO.id}")
                val newSchedule = updateScheduleEntry(entryDTO.apply {
                    categoryIds = ((categoryIds ?: emptyArray()) + f.categoryId).distinct().toTypedArray()
                    fightIds = ((fightIds ?: emptyArray()) + MatIdAndSomeId(mat.id, f.id)).distinct().toTypedArray()
                    startTime = startTime ?: mat.currentTime
                    numberOfFights = (numberOfFights ?: 0) + 1
                })
                return newSchedule to updateMatInCollection(
                        mat.copy(
                                currentTime = mat.currentTime.plus(Duration.ofMinutes(getFightDuration(f, mat.periodId).toLong())),
                                fights = mat.fights +
                                        InternalFightStartTime(
                                                fight = f,
                                                fightNumber = mat.totalFights,
                                                startTime = mat.currentTime,
                                                matId = mat.id,
                                                periodId = mat.periodId),
                                totalFights = mat.totalFights + 1))
            } else {
                log.info("Fight ${f.id} should be started later. Adding it to pending.")
                return schedule to updateMatInCollection(mat.copy(pending = LinkedHashSet(mat.pending + f.withOrder(requirementForFight?.entryOrder))))
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
        log.info("Pauses $pauses \nmat: $mat, \nfight: $f \nrequirement: $requirementForFight")
        return requirementForFight?.entryOrder?.let { entryOrder ->
            pauses.filter { it.entryType == ScheduleRequirementType.RELATIVE_PAUSE }
                    .find {
                        it.entryOrder <= entryOrder
                                && it.matId == mat.id
                                && it.periodId == mat.periodId
                    }
        }
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


    private inline fun prevReqPredicate(requirement: ScheduleRequirementDTO, crossinline matCondition: (s: ScheduleRequirementDTO) -> Boolean) = { it: ScheduleRequirementDTO ->
        it.id != requirement.id
                && it.entryType != ScheduleRequirementType.FIXED_PAUSE
                && it.entryType != ScheduleRequirementType.RELATIVE_PAUSE
                && it.entryOrder < requirement.entryOrder
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
                        .setEntryType(ScheduleEntryType.FIGHTS_GROUP)
                        .setFightIds(emptyArray())
                        .setCategoryIds(emptyArray())
                        .setStartTime(requirement.startTime)
                        .setEndTime(requirement.endTime)
                        .setRequirementIds(arrayOf(requirement.id))
                        .setName(requirement.name)
                        .setColor(requirement.color))
    }

    private fun acceptFight(schedule: List<ScheduleEntryDTO>, fightsByMats: List<InternalMatScheduleContainer>, f: FightDescription, pauses: MutableList<ScheduleRequirementDTO>, lastrun: Boolean): Pair<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>> {
        return this.updateSchedule(f, schedule, fightsByMats, pauses, lastrun)
    }

    private fun getFightDuration(fight: FightDescription, periodId: String) = fight.duration!!.multiply(riskCoeff[periodId]
            ?: error("No risk coeff for $periodId")).plus(timeBetweenFights[periodId]
            ?: error("No TimeBetweenFights for $periodId"))

    private fun <T> MutableList<T>.replaceAll(elements: Collection<T>) {
        this.clear()
        this.addAll(elements)
    }

    fun simulate(): Mono<Tuple3<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>, Set<String>>> {
        return this.brackets.buffer(mats.size + 1 /* на всякий :) */)
                .collect(this)
                .map { Tuple3(it.a, it.b, it.d) }
    }

    override fun characteristics(): MutableSet<Collector.Characteristics> {
        return mutableSetOf(Collector.Characteristics.CONCURRENT)
    }

    override fun supplier(): Supplier<ScheduleAccumulator> {
        val initialFightsByMats = mats.mapIndexed { i, mat ->
            val initDate = ZonedDateTime.ofInstant(startTime[mat.periodId]
                    ?: error("No Start time for period ${mat.periodId}"), ZoneId.of(timeZone))
            InternalMatScheduleContainer(
                    timeZone = timeZone,
                    name = mat.name,
                    id = mat.id ?: IDGenerator.createMatId(mat.periodId),
                    fights = emptyList(),
                    currentTime = initDate.toInstant(),
                    totalFights = 0,
                    pending = LinkedHashSet(),
                    matOrder = mat.matOrder ?: i,
                    periodId = mat.periodId, invalid = LinkedHashSet())
        }.toMutableList()

        return Supplier {
            log.info("Supplier.")
            ScheduleAccumulator(initialFightsByMats)
        }
    }

    override fun accumulator(): BiConsumer<ScheduleAccumulator, List<IBracketSimulator>> {
        return BiConsumer { fightsByMats: ScheduleAccumulator, brackets: List<IBracketSimulator> ->
            val br = brackets.toMutableList()
            log.info("Consumer.")
            val activeBrackets = ArrayList<IBracketSimulator>()
            var pendingFights = fightsByMats.pendingFights.toList()
            var sfbm = fightsByMats.scheduleEntries.toList() to fightsByMats.matSchedules.toList()
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
                val fightsToDispatch = (pendingFights + fights.map{it.withOrder(-1)}).distinctBy { it.f.id }.sortedBy { it.entryOrder }.map { it.f }
                sfbm = fightsToDispatch.fold(sfbm) { acc, f ->
                    this.acceptFight(acc.first, acc.second, f, pauses, false)
                }
                pendingFights = sfbm.second.flatMap { it.pending }
                sfbm = sfbm.copy(second = sfbm.second.map { it.copy(pending = LinkedHashSet()) })
            }
            val invalidFights = sfbm.second.flatMap { internalMatScheduleContainer ->
                internalMatScheduleContainer.invalid.map { it.id }
            }
            fightsByMats.scheduleEntries.replaceAll(sfbm.first)
            fightsByMats.matSchedules.replaceAll(sfbm.second)
            fightsByMats.pendingFights.replaceAll(pendingFights)
            fightsByMats.invalidFights.addAll(invalidFights)
        }
    }

    override fun combiner(): BinaryOperator<ScheduleAccumulator> {
        return BinaryOperator { t: ScheduleAccumulator, u: ScheduleAccumulator ->
            log.info("Combiner.")
            val b = t.matSchedules.map { mat ->
                u.matSchedules.find { m -> m.id == mat.id }
                        ?.let { scheduleContainer ->
                            val newFights = (mat.fights + scheduleContainer.fights.filter { f -> mat.fights.none { it.fight.id == f.fight.id } })
                                    .sortedBy { fightStartTime -> fightStartTime.startTime }
                                    .mapIndexed { ind, f ->
                                        f.copy(fightNumber = ind)
                                    }
                            mat.copy(fights = newFights, totalFights = newFights.size)
                        } ?: mat
            }.toMutableList()
            val a = (t.scheduleEntries + u.scheduleEntries).groupBy { it.id }.mapValues { e ->
                //Categories, fights, requirements
                val cfr = e.value.fold(Tuple3(emptyList<String>(), emptyList<MatIdAndSomeId>(), emptyList<String>())) { acc, schedEntry ->
                    Tuple3((acc.a + schedEntry.categoryIds.orEmpty()).distinct(),
                            (acc.b + schedEntry.fightIds.orEmpty()).distinct(),
                            (acc.c + schedEntry.requirementIds.orEmpty()).distinct())
                }
                val entry = e.value[0]
                entry
                        .setCategoryIds(cfr.a.distinct().toTypedArray())
                        .setFightIds(cfr.b.distinct().toTypedArray())
                        .setRequirementIds(cfr.c.distinct().toTypedArray())
            }.toList().map { it.second }.toMutableList()
            ScheduleAccumulator(t.matSchedules.toList()).apply {
                scheduleEntries.addAll(a)
                matSchedules.addAll(b)
                pendingFights.addAll((t.pendingFights + u.pendingFights).distinctBy { it.f.id })
                invalidFights.addAll(t.invalidFights + u.invalidFights)
            }
        }
    }

    override fun finisher(): java.util.function.Function<ScheduleAccumulator,
            Tuple4<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>, List<FightDescription>, Set<String>>> {
        return java.util.function.Function<ScheduleAccumulator,
                Tuple4<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>, List<FightDescription>, Set<String>>> { scheduleAccumulator ->
            log.info("Finisher.")
            var k = scheduleAccumulator.pendingFights.distinctBy { it.f.id }.sortedBy { it.entryOrder }
                    .fold(scheduleAccumulator.scheduleEntries.toList() to scheduleAccumulator.matSchedules.toList()) { acc, f ->
                        this.acceptFight(acc.first, acc.second, f.f, pauses, true) }
            while (!k.second.flatMap { container -> container.pending }.isNullOrEmpty()) {
                k = k.second.flatMap { container -> container.pending }
                        .distinctBy { it.f.id }
                        .sortedBy { it.entryOrder }
                        .fold(k.first to k.second.map { scheduleContainer ->
                            scheduleContainer
                                    .copy(pending = LinkedHashSet())
                        }) { acc, f ->
                            this.acceptFight(acc.first, acc.second, f.f, pauses, true)
                        }
            }
            Tuple4(k.first, k.second, listOf(), scheduleAccumulator.invalidFights)
        }
    }
}