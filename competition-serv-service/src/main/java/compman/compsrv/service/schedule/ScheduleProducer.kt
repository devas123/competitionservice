package compman.compsrv.service.schedule

import arrow.core.Tuple3
import arrow.core.Tuple4
import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.repository.collectors.ScheduleEntryAccumulator
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Supplier
import java.util.stream.Collector

data class InternalFightStartTime(val fightId: String,
                                  val categoryId: String,
                                  val round: Int,
                                  val matId: String,
                                  val fightNumber: Int,
                                  val startTime: Instant,
                                  val scheduleEntryId: String,
                                  val periodId: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InternalFightStartTime

        if (fightId != other.fightId) return false

        return true
    }

    override fun hashCode(): Int {
        return fightId.hashCode()
    }
}

data class FightDescriptionAndEntryOrder(val f: FightDescriptionWithParentIds, val entryOrder: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FightDescriptionAndEntryOrder

        if (f.fight.id != other.f.fight.id) return false

        return true
    }

    override fun hashCode(): Int {
        return f.fight.id.hashCode()
    }
}

fun FightDescriptionWithParentIds.withOrder(order: Int?) = FightDescriptionAndEntryOrder(this, order ?: Int.MAX_VALUE)

class InternalMatScheduleContainer(
        var currentTime: Instant,
        var name: String,
        var matOrder: Int?,
        var totalFights: Int,
        var id: String,
        var periodId: String,
        var fights: MutableList<InternalFightStartTime>,
        var timeZone: String) {

    fun addFight(f: InternalFightStartTime): InternalMatScheduleContainer {
        fights.add(f)
        return this
    }

    fun copy(currentTime: Instant = this.currentTime, name: String = this.name, matOrder: Int? = this.matOrder,
             totalFights: Int = this.totalFights, id: String = this.id, periodId: String = this.periodId,
             fights: MutableList<InternalFightStartTime> = this.fights, timeZone: String = this.timeZone): InternalMatScheduleContainer {
        this.currentTime = currentTime
        this.name = name
        this.matOrder = matOrder
        this.totalFights = totalFights
        this.id = id
        this.periodId = periodId
        this.fights = fights
        this.timeZone = timeZone
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as InternalMatScheduleContainer

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}

class ScheduleAccumulator(initialMatSchedules: List<InternalMatScheduleContainer>) {
    val scheduleEntries = mutableListOf<ScheduleEntryAccumulator>()
    val matSchedules = ArrayList(initialMatSchedules)
    val pendingFights = HashSet<FightDescriptionAndEntryOrder>()
    val invalidFights = HashSet<String>()
    val dispatchedFightIds = HashSet<String>()

    fun getPendingFightsList() = pendingFights.filter { !dispatchedFightIds.contains(it.f.fight.id) }
            .distinctBy { it.f.fight.id }.sortedBy { it.entryOrder }
}


class ScheduleProducer(val competitionId: String,
                       val startTime: Map<String, Instant>,
                       val mats: List<MatDescriptionDTO>,
                       req: List<ScheduleRequirementDTO>,
                       private val brackets: Flux<StageGraph>,
                       val timeBetweenFights: Map<String, BigDecimal>,
                       riskFactor: Map<String, BigDecimal>,
                       val timeZone: String) : Collector<List<IBracketSimulator>, ScheduleAccumulator, Tuple4<List<ScheduleEntryDTO>,
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

    private fun internalMatById2(fightsByMats: MutableList<InternalMatScheduleContainer>) = { matId: String -> fightsByMats.indexOfFirst { it.id == matId } }

    private fun findRequirementForFight(f: FightDescription) =
            this.scheduleRequirements
                    .firstOrNull {
                        it.entryType == ScheduleRequirementType.FIGHTS && (it.fightIds?.contains(f.id) == true)
                    }
                    ?: this.scheduleRequirements.firstOrNull {
                        it.entryType == ScheduleRequirementType.CATEGORIES && (it.categoryIds?.contains(f.categoryId) == true)
                    }

    private fun updateScheduleEntry2(schedule: MutableList<ScheduleEntryAccumulator>) = { newEntryDTO: ScheduleEntryAccumulator ->
        schedule.removeIf { it.getId() == newEntryDTO.getId() }
        schedule.add(newEntryDTO)
    }

    private fun eightyPercentOfDurationInMillis(duration: BigDecimal): Long {
        return duration.multiply(BigDecimal.valueOf(0.8 * 60000)).toLong()
    }

    private fun fightsAreDispatchedOrCanBeDispatched(fightIds: Set<String>, dispatchedFights: Set<String>, schedule: MutableList<ScheduleEntryAccumulator>, currentTime: Instant): Boolean {
        return fightIds.filter { f -> !dispatchedFights.contains(f) }.fold(true) { acc, fid ->
            acc && scheduleRequirements.any { sr ->
                (sr.fightIds?.contains(fid) == true
                        && schedule.any { se -> se.getRequirementIds()?.contains(sr.id) == true }) ||
                        startTime[sr.periodId]?.toEpochMilli()!! <= currentTime.toEpochMilli()
            }
        }
    }

    private fun updateSchedule(accumulator: ScheduleAccumulator,
                               f: FightDescriptionWithParentIds,
                               pauses: MutableList<ScheduleRequirementDTO>,
                               lastRun: Boolean): ScheduleAccumulator {
        val updateScheduleEntry = updateScheduleEntry2(accumulator.scheduleEntries)
        val internalMatById = internalMatById2(accumulator.matSchedules)
        if (!accumulator.dispatchedFightIds.contains(f.fight.id)) {
            val requirementForFight = findRequirementForFight(f.fight)
            val periodId = requirementForFight?.periodId
            val existingIndex = accumulator.matSchedules.indexOfFirst { (periodId.isNullOrBlank() || it.periodId == periodId) && it.fights.isEmpty() }
            val defaultMatIndex = if (existingIndex >= 0) {
                existingIndex
            } else {
                val mat = accumulator.matSchedules.filter { periodId.isNullOrBlank() || it.periodId == periodId }
                        .minBy { a -> a.currentTime.toEpochMilli() }!!
                accumulator.matSchedules.indexOf(mat)
            }
            val entryDTO = when {
                requirementForFight?.categoryIds?.contains(f.fight.categoryId) == true || requirementForFight?.fightIds?.contains(f.fight.id) == true -> {
                    val e = scheduleEntryFromRequirement(requirementForFight, accumulator.scheduleEntries)
                    log.trace("Fight ${f.fight.id} from category ${f.fight.categoryId} has requirements. ${e.getId()}")
                    e
                }
                else -> {
                    log.trace("Neither fight category ${f.fight.categoryId} nor fight itself ${f.fight.id} was dispatched. Placing it to random mat.")
                    accumulator.scheduleEntries.find { it.categoryIds.contains(f.fight.categoryId) && it.getRequirementIds().isNullOrEmpty() }
                            ?: emptyScheduleEntry(accumulator.matSchedules[defaultMatIndex])
                }
            }
            val matIndex = requirementForFight?.matId?.let { internalMatById(it) } ?: defaultMatIndex
            val currentMat = accumulator.matSchedules.getOrElse(matIndex) { accumulator.matSchedules[0] }
            val fightParents = f.parentIds
            if (!fightsAreDispatchedOrCanBeDispatched(fightParents, accumulator.dispatchedFightIds, accumulator.scheduleEntries, currentMat.currentTime) && !lastRun) {
                log.trace("Fight $f cannot be dispatched because it's parent fights $fightParents have incorrect order.")
                accumulator.pendingFights.add(f.withOrder(requirementForFight?.entryOrder))
                accumulator.invalidFights.add(f.fight.id)
                return accumulator
            }
            if ((requirementForFight != null && !previousRequirementsMet(requirementForFight, accumulator.scheduleEntries))) {
                accumulator.pendingFights.add(f.withOrder(requirementForFight.entryOrder))
                return accumulator
            }
            val matWithTheSameCategory = accumulator.matSchedules
                    .find {
                        val lastFight = it.fights.lastOrNull()
                        it.periodId == currentMat.periodId
                                && lastFight != null
                                && lastFight.categoryId == f.fight.categoryId
                                && ((f.fight.round ?: -1) - lastFight.round) in 1..2
                    }
            if (matWithTheSameCategory != null && !lastRun) {
                log.trace("Sending fight ${f.fight.categoryId} -> ${f.fight.round} to pending.")
                accumulator.pendingFights.add(f.withOrder(requirementForFight?.entryOrder))
                return accumulator
            }
            val fixedPause = findFixedPauseForMatAndFight(pauses, currentMat, f.fight)
            if (fixedPause != null) {
                pauses.removeIf { it.id == fixedPause.id }
                log.trace("Fixed pause.")
                val endTime = fixedPause.endTime
                        ?: fixedPause.startTime.plusMillis(fixedPause.durationMinutes.multiply(BigDecimal.valueOf(60000L)).toLong())
                val pauseEntry = createFixedPauseEntry(fixedPause, endTime)
                accumulator.pendingFights.add(f.withOrder(requirementForFight?.entryOrder))
                updateScheduleEntry(ScheduleEntryAccumulator(pauseEntry))
                currentMat.currentTime = endTime
                return accumulator
            }
            val relativePause = findRelativePauseForMatAndFight(pauses, currentMat, f.fight, requirementForFight)
            if (relativePause != null) {
                pauses.removeIf { it.id == relativePause.id }
                log.trace("Relative pause.")
                val endTime = currentMat.currentTime.plusMillis(relativePause.durationMinutes.multiply(BigDecimal.valueOf(60000L)).toLong())
                val pauseEntry = createRelativePauseEntry(relativePause, accumulator.matSchedules[matIndex].currentTime, endTime)
                accumulator.pendingFights.add(f.withOrder(requirementForFight?.entryOrder))
                updateScheduleEntry(ScheduleEntryAccumulator(pauseEntry))
                currentMat.currentTime = endTime
                return accumulator
            }
            if (!accumulator.dispatchedFightIds.contains(f.fight.id) && (entryDTO.getStartTime()?.isBefore(currentMat.currentTime.plusSeconds(60)) != false || lastRun)) {
                log.trace("Dispatching fight ${f.fight.id} -> ${f.fight.round}. to entry ${entryDTO.getId()}")
                updateScheduleEntry(entryDTO.apply {
                    categoryIds.add(f.fight.categoryId)
                    setStartTime(getStartTime() ?: currentMat.currentTime)
                    setNumberOfFights((getNumberOfFights() ?: 0) + 1)
                })
                currentMat.addFight(InternalFightStartTime(
                        fightId = f.fight.id,
                        categoryId = f.fight.categoryId,
                        round = f.fight.round,
                        fightNumber = currentMat.totalFights,
                        startTime = currentMat.currentTime,
                        matId = currentMat.id,
                        scheduleEntryId = entryDTO.getId(),
                        periodId = currentMat.periodId))
                currentMat.totalFights += 1
                currentMat.currentTime = currentMat.currentTime.plus(Duration.ofMinutes(getFightDuration(f.fight, currentMat.periodId).toLong()))
                accumulator.pendingFights.removeIf { it.f.fight.id == f.fight.id }
                accumulator.dispatchedFightIds.add(f.fight.id)
            } else if (!accumulator.dispatchedFightIds.contains(f.fight.id)) {
                log.trace("Fight ${f.fight.id} should be started later. Adding it to pending.")
                accumulator.pendingFights.add(f.withOrder(requirementForFight?.entryOrder))
            }
        } else {
            log.trace("Fight $f is already registered. Skipping.")
        }
        return accumulator

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
        log.trace("Pauses $pauses \nmat: $mat, \nfight: $f \nrequirement: $requirementForFight")
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


    private fun previousRequirementsMet(requirement: ScheduleRequirementDTO, schedule: MutableList<ScheduleEntryAccumulator>): Boolean {
        val previousRequirementIds = if (requirement.matId.isNullOrBlank()) {
            //This is a requirement without mat
            scheduleRequirements.filter(prevReqPredicate(requirement) { s -> s.matId.isNullOrBlank() })
        } else {
            //This is a requirement for mat
            scheduleRequirements.filter(prevReqPredicate(requirement) { s -> !s.matId.isNullOrBlank() })
        }
        return previousRequirementIds.isEmpty() || previousRequirementIds.all { requirementId ->
            schedule.any { it.getRequirementIds()?.contains(requirementId.id) == true }
        }
    }

    private fun emptyScheduleEntry(defaultMat: InternalMatScheduleContainer): ScheduleEntryAccumulator {
        return ScheduleEntryAccumulator(ScheduleEntryDTO()
                .setId(IDGenerator
                        .scheduleEntryId(competitionId, defaultMat.periodId))
                .setPeriodId(defaultMat.periodId)
                .setEntryType(ScheduleEntryType.FIGHTS_GROUP)
                .setFightIds(emptyArray())
                .setCategoryIds(emptyArray())
                .setStartTime(defaultMat.currentTime)
                .setRequirementIds(emptyArray()))
    }

    private fun scheduleEntryFromRequirement(requirement: ScheduleRequirementDTO, schedule: MutableList<ScheduleEntryAccumulator>): ScheduleEntryAccumulator {
        return (schedule.find { it.getRequirementIds()?.contains(requirement.id) == true }
                ?: ScheduleEntryAccumulator(ScheduleEntryDTO()
                        .setId(IDGenerator
                                .scheduleEntryId(competitionId, requirement.periodId))
                        .setPeriodId(requirement.periodId)
                        .setEntryType(ScheduleEntryType.FIGHTS_GROUP)
                        .setFightIds(emptyArray())
                        .setCategoryIds(emptyArray())
                        .setStartTime(requirement.startTime)
                        .setEndTime(requirement.endTime)
                        .setRequirementIds(arrayOf(requirement.id))
                        .setName(requirement.name)
                        .setColor(requirement.color)))
    }

    private fun getFightDuration(fight: FightDescription, periodId: String) = fight.duration!!.multiply(riskCoeff[periodId]
            ?: error("No risk coeff for $periodId")).plus(timeBetweenFights[periodId] ?: BigDecimal.ZERO)

    fun simulate(): Mono<Tuple3<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>, Set<String>>> {
        return this.brackets.buffer(mats.size + 1 /* на всякий :) */)
                .collect(this)
                .map { Tuple3(it.a, it.b, it.d) }
    }

    override fun characteristics(): MutableSet<Collector.Characteristics> {
        return mutableSetOf(Collector.Characteristics.CONCURRENT)
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

    override fun supplier(): Supplier<ScheduleAccumulator> {
        return Supplier {
            log.info("Supplier.")
            ScheduleAccumulator(initialFightsByMats)
        }
    }

    override fun accumulator(): BiConsumer<ScheduleAccumulator, List<IBracketSimulator>> {
        return BiConsumer { accumulator: ScheduleAccumulator, brackets: List<IBracketSimulator> ->
            val br = brackets.toMutableList()
            log.info("Consumer.")
            val activeBrackets = ArrayList<IBracketSimulator>()
            var pendingFights = accumulator.getPendingFightsList()
            while (br.isNotEmpty() || activeBrackets.isNotEmpty()) {
                val fights = ArrayList<FightDescriptionWithParentIds>()
                var i = 0
                if (activeBrackets.size >= i + 1) {
                    fights.addAll(activeBrackets[i++].getNextRound())
                }
                while (fights.size <= mats.size && br.isNotEmpty()) {
                    if (activeBrackets.getOrNull(i) == null || activeBrackets.getOrNull(i)?.isEmpty() != false) {
                        activeBrackets.add(br.removeAt(0))
                    }
                    fights.addAll(activeBrackets[i++].getNextRound())
                }
                activeBrackets.removeIf { b -> b.isEmpty() }
                val fightsToDispatch = (pendingFights + fights.map { it.withOrder(-1) }).distinctBy { it.f.fight.id }.sortedBy { it.entryOrder }.map { it.f }
                        .filter { !accumulator.dispatchedFightIds.contains(it.fight.id) }
                val start = System.currentTimeMillis()
                log.info("Started folding. Fights size: ${fightsToDispatch.size}")
                fightsToDispatch.fold(accumulator) { acc, f ->
                    this.updateSchedule(acc, f, pauses, false)
                }
                log.info("Finished folding. Took ${System.currentTimeMillis() - start}ms.")
                pendingFights = accumulator.getPendingFightsList()
                accumulator.pendingFights.clear()
//                sfbm = sfbm.copy(second = sfbm.second.map { it.copy(pending = LinkedHashSet()) })
            }
            accumulator.pendingFights.addAll(pendingFights)
        }
    }

    override fun combiner(): BinaryOperator<ScheduleAccumulator> {
        return BinaryOperator { t: ScheduleAccumulator, u: ScheduleAccumulator ->
            log.info("Combiner.")
            val b = t.matSchedules.map { mat ->
                u.matSchedules.find { m -> m.id == mat.id }
                        ?.let { scheduleContainer ->
                            val newFights = (mat.fights + scheduleContainer.fights.filter { f -> mat.fights.none { it.fightId == f.fightId } })
                                    .sortedBy { fightStartTime -> fightStartTime.startTime }
                                    .mapIndexed { ind, f ->
                                        f.copy(fightNumber = ind)
                                    }
                            mat.copy(fights = newFights.toMutableList(), totalFights = newFights.size)
                        } ?: mat
            }.toMutableList()
            val a = (t.scheduleEntries + u.scheduleEntries).groupBy { it.getRequirementIds() }.mapValues { e ->
                //Categories, fights, requirements
                val cfr = e.value.fold(Tuple3(emptyList<String>(), emptyList<MatIdAndSomeId>(), emptyList<String>())) { acc, schedEntry ->
                    Tuple3((acc.a + schedEntry.categoryIds).distinct(),
                            (acc.b + schedEntry.fightIds).distinct(),
                            (acc.c + schedEntry.getRequirementIds().orEmpty()).distinct())
                }
                val entry = e.value[0]
                entry.apply {
                    categoryIds.addAll(cfr.a)
                    setRequirementIds(cfr.c.distinct().toTypedArray())
                }

            }.toList().map { it.second }.toMutableList()
            ScheduleAccumulator(t.matSchedules.toList()).apply {
                scheduleEntries.addAll(a)
                matSchedules.addAll(b)
                pendingFights.addAll((t.pendingFights + u.pendingFights).distinctBy { it.f.fight.id })
                invalidFights.addAll(t.invalidFights + u.invalidFights)
            }
        }
    }

    override fun finisher(): java.util.function.Function<ScheduleAccumulator,
            Tuple4<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>, List<FightDescription>, Set<String>>> {
        return java.util.function.Function<ScheduleAccumulator,
                Tuple4<List<ScheduleEntryDTO>, List<InternalMatScheduleContainer>, List<FightDescription>, Set<String>>> { scheduleAccumulator ->
            log.info("Finisher.")
            var pending = scheduleAccumulator.getPendingFightsList()
            scheduleAccumulator.pendingFights.clear()
            pending
                    .fold(scheduleAccumulator) { acc, f ->
                        this.updateSchedule(acc, f.f, pauses, true)
                    }
            pending = scheduleAccumulator.getPendingFightsList()
            scheduleAccumulator.pendingFights.clear()
            while (!pending.isNullOrEmpty()) {
                pending
                        .fold(scheduleAccumulator) { acc, f ->
                            this.updateSchedule(acc, f.f, pauses, true)
                        }
                pending = scheduleAccumulator.getPendingFightsList()
                scheduleAccumulator.pendingFights.clear()
            }
            Tuple4(scheduleAccumulator.scheduleEntries.map { it.getScheduleEntry() }, scheduleAccumulator.matSchedules.toList(), listOf(), scheduleAccumulator.invalidFights)
        }
    }
}