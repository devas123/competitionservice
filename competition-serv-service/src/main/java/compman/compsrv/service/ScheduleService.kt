package compman.compsrv.service

import arrow.core.Tuple3
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
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector
import kotlin.collections.ArrayList

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
            val totalFights: Int,
            val id: String,
            val periodId: String,
            val fights: List<InternalFightStartTime>,
            val timeZone: String,
            val pending: List<FightDescription>)


    private class ScheduleComposer(val startTime: Map<String, Instant>,
                                   val mats: List<MatDescriptionDTO>,
                                   private val scheduleRequirements: List<ScheduleRequirementDTO>,
                                   private val brackets: Flux<IBracketSimulator>,
                                   val timeBetweenFights: Map<String, BigDecimal>,
                                   riskFactor: Map<String, BigDecimal>,
                                   val timeZone: String) {
        private val schedule: MutableList<ScheduleEntryDTO> = mutableListOf()
        fun schedule() = schedule.toList().sortedBy { it.startTime }

        val pauses = scheduleRequirements.filter { it.entryType == ScheduleRequirementType.PAUSE }.toMutableList()

        val riskCoeff = riskFactor.mapValues { BigDecimal.ONE.plus(it.value) }

        fun categoryNotRegistered(categoryId: String): Boolean {
            return this.schedule.none { it.categoryIds?.contains(categoryId) == true }
        }

        private fun internalMatById2(fightsByMats: List<InternalMatScheduleContainer>) = { matId: String -> fightsByMats.first { it.id == matId } }

        fun fightNotRegistered(fightId: String) =
                this.schedule.none { it.fightIds?.contains(fightId) == true }

        fun haveRequirementsForFight(fightId: String) =
                this.scheduleRequirements.any { it.entryType == ScheduleRequirementType.FIGHTS && it.fightIds?.contains(fightId) == true }

        fun haveRequirementsForCategory(categoryId: String) =
                this.scheduleRequirements.any { it.entryType == ScheduleRequirementType.CATEGORIES && it.categoryIds?.contains(categoryId) == true }

        fun updateMatInCollection2(fightsByMats: List<InternalMatScheduleContainer>) = { freshMatCopy: InternalMatScheduleContainer ->
            fightsByMats.map {
                if (it.id == freshMatCopy.id) {
                    freshMatCopy
                } else {
                    it
                }
            }
        }


        fun updateSchedule(f: FightDescription, fightsByMats: List<InternalMatScheduleContainer>, lastRun: Boolean): List<InternalMatScheduleContainer> {
            val updateMatInCollection = updateMatInCollection2(fightsByMats)
            val internalMatById = internalMatById2(fightsByMats)
            log.debug("Updating schedule, fight: ${f.id}")
            if (fightNotRegistered(f.id)) {
                val entryDTO = when {
                    haveRequirementsForFight(f.id) -> {
                        log.debug("Fight ${f.id} has requirements.")
                        scheduleEntryFromRequirement(this.scheduleRequirements.first { it.fightIds?.contains(f.id) == true })
                    }
                    haveRequirementsForCategory(f.categoryId) -> {
                        log.debug("Category ${f.categoryId} has requirements.")
                        scheduleEntryFromRequirement(this.scheduleRequirements.first { it.categoryIds?.contains(f.categoryId) == true })
                    }
                    else -> {
                        log.warn("Neither category ${f.categoryId} nor fight ${f.id} was dispatched. Placing it to random mat")
                        val defaultMat = fightsByMats.find { it.fights.isEmpty() }
                                ?: fightsByMats.minBy { a -> a.currentTime.toEpochMilli() }!!
                        this.schedule.firstOrNull { it.categoryIds?.contains(f.categoryId) == true && it.requirementIds.isNullOrEmpty() }
                                ?: ScheduleEntryDTO()
                                        .setId(UUID.randomUUID().toString())
                                        .setPeriodId(defaultMat.periodId)
                                        .setEntryType(ScheduleEntryType.FIGHTS_GROUP)
                                        .setFightIds(emptyArray())
                                        .setCategoryIds(emptyArray())
                                        .setStartTime(defaultMat.currentTime)
                                        .setRequirementIds(emptyArray())
                    }
                }
                val defaultMat = fightsByMats.find { it.fights.isEmpty() && it.periodId == entryDTO.periodId }
                        ?: fightsByMats.filter { it.periodId == entryDTO.periodId }.minBy { a -> a.currentTime.toEpochMilli() }!!
                val m = entryDTO.matId ?: defaultMat.id
                val mat = internalMatById(m)

                val matsWithTheSameCategory = fightsByMats
                        .filter {
                            it.fights.isNotEmpty() && it.fights.last().fight.categoryId == f.categoryId
                                    && (it.fights.last().fight.round ?: -1) < (f.round ?: -1)
                        }
                if (matsWithTheSameCategory.isNotEmpty() && !lastRun) {
                    val matWithTheSameCat = matsWithTheSameCategory.minBy { it.currentTime.toEpochMilli() }!!
                    return updateMatInCollection(matWithTheSameCat.copy(pending = matWithTheSameCat.pending + f))
                }

                if (this.pauses.any { it.startTime <= mat.currentTime && it.matId == m }) {
                    val pause = this.pauses.first { it.startTime <= mat.currentTime }
                    this.schedule.add(ScheduleEntryDTO().apply {
                        id = pause.id
                        this.matId = m
                        categoryIds = emptyArray()
                        fightIds = emptyArray()
                        startTime = mat.currentTime
                        numberOfFights = 0
                        entryType = ScheduleEntryType.PAUSE
                        endTime = pause.endTime!!
                        requirementIds = arrayOf(pause.id)
                    })
                    pauses.removeIf { it.id == pause.id }
                    return updateMatInCollection(
                            mat.copy(currentTime = pause.endTime!!, pending = mat.pending + f))
                }
                if (entryDTO.startTime == null || entryDTO.startTime <= mat.currentTime) {
                    log.debug("Fight ${f.id} meets start time requirements.")
                    if (this.categoryNotRegistered(f.categoryId)) {
                        log.debug("Category ${f.categoryId} was not registered, creating new schedule entry for it")
                        this.schedule.removeIf { it.id == entryDTO.id }
                        this.schedule.add(entryDTO.apply {
                            categoryIds = (categoryIds ?: emptyArray()) + f.categoryId
                            fightIds = (fightIds ?: emptyArray()) + f.id
                            startTime = startTime ?: mat.currentTime
                            numberOfFights = (numberOfFights ?: 0) + 1
                        })
                    } else {
                        log.debug("Category ${f.categoryId} was registered.")
                        val entry = this.schedule.first { it.categoryIds.contains(f.categoryId) }
                        entry.fightIds = (entry.fightIds ?: emptyArray()) + f.id
                        entry.numberOfFights = (entry.numberOfFights ?: 0) + 1
                    }
                    return updateMatInCollection(
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
                    return updateMatInCollection(mat.copy(pending = mat.pending + f))
                }
            } else {
                log.warn("Fight $f is already registered. Skipping.")
                return fightsByMats
            }
        }

        private fun scheduleEntryFromRequirement(requirement: ScheduleRequirementDTO): ScheduleEntryDTO {
            return (this.schedule.firstOrNull { it.requirementIds?.contains(requirement.id) == true }
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

        fun acceptFight(fightsByMats: List<InternalMatScheduleContainer>, f: FightDescription, lastrun: Boolean): List<InternalMatScheduleContainer> {
            return this.updateSchedule(f, fightsByMats, lastrun)
        }

        fun getFightDuration(fight: FightDescription, periodId: String) = fight.duration!!.multiply(riskCoeff[periodId]
                ?: error("No risk coeff for $periodId")).plus(timeBetweenFights[periodId]
                ?: error("No TimeBetweenFights for $periodId"))

        fun simulate(): Mono<List<InternalMatScheduleContainer>> {
            val activeBrackets = ArrayList<IBracketSimulator>()
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
                        pending = emptyList(),
                        periodId = mat.periodId)
            }.toMutableList()


            return this.brackets.buffer(initialFightsByMats.size + 1 /* на всякий :) */)
                    .collect(Collector.of(
                            Supplier { initialFightsByMats to mutableListOf<FightDescription>() },
                            BiConsumer<Pair<MutableList<InternalMatScheduleContainer>, MutableList<FightDescription>>, MutableList<IBracketSimulator>> { fightsByMats, br ->
                        var fbm = fightsByMats.first.toList()
                        var pendingFights = fightsByMats.second.toList()
                        while (br.isNotEmpty() || activeBrackets.isNotEmpty()) {
                            val fights = ArrayList<FightDescription>()
                            var i = 0
                            if (activeBrackets.getOrNull(i) != null) {
                                fights.addAll(activeBrackets[i++].getNextRound())
                            }
                            while (fights.size <= mats.size && br.isNotEmpty()) {
                                if (activeBrackets.getOrNull(i) == null) {
                                    activeBrackets.add(br.removeAt(0))
                                }
                                fights.addAll(activeBrackets[i++].getNextRound())
                            }

                            activeBrackets.removeIf { b -> b.isEmpty() }
                            fbm = (pendingFights + fights).fold(fbm) { acc, f ->
                                this.acceptFight(acc, f, false)
                            }
                            pendingFights = fbm.flatMap { it.pending }
                            fbm = fbm.map { it.copy(pending = emptyList()) }
                        }
                                fightsByMats.first.clear()
                                fightsByMats.first.addAll(fbm)
                                fightsByMats.second.clear()
                                fightsByMats.second.addAll(pendingFights)
                    }, BinaryOperator { t, u ->
                     log.info("Combiner.")
                        t.first.map { mat -> u.first.find { m -> m.id == mat.id }
                             ?.let { scheduleContainer ->
                                 val newFights = (mat.fights + scheduleContainer.fights.filter { f -> mat.fights.none { it.fight.id == f.fight.id }})
                                         .sortedBy { fightStartTime -> fightStartTime.startTime }
                                         .mapIndexed { ind, f ->
                                             f.copy(fightNumber = ind + 1)
                                 }
                                 mat.copy(fights = newFights, totalFights = newFights.size)
                             } ?: mat}.toMutableList() to t.second
                    }, Function<Pair<MutableList<InternalMatScheduleContainer>, MutableList<FightDescription>>, Pair<MutableList<InternalMatScheduleContainer>, MutableList<FightDescription>>> {
                        log.info("Finisher.")
                        it.second.fold(it.first.toList()) { acc, f -> this.acceptFight(acc, f, true) }.toMutableList() to mutableListOf() }, Collector.Characteristics.IDENTITY_FINISH))
                    .map { it.first }
        }
    }

    /**
     * @param stages - Flux<pair<Tuple3<StageId, CategoryId, BracketType>, fights>>
     */
    fun generateSchedule(competitionId: String, properties: List<PeriodDTO>, stages: Flux<Pair<Tuple3<String, String, BracketType>, List<FightDescription>>>, timeZone: String,
                         categoryCompetitorNumbers: Map<String, Int>): ScheduleDTO {
        if (!properties.isNullOrEmpty()) {
            return doGenerateSchedule(competitionId, stages, properties, timeZone, categoryCompetitorNumbers)
        } else {
            throw ServiceException("Periods are not specified!")
        }
    }

    private fun doGenerateSchedule(competitionId: String,
                                   stages: Flux<Pair<Tuple3<String, String, BracketType>, List<FightDescription>>>,
                                   periods: List<PeriodDTO>,
                                   timeZone: String,
                                   categoryCompetitorNumbers: Map<String, Int>): ScheduleDTO {
        val periodsWithIds = periods.map { periodDTO ->
            val id = periodDTO.id ?: IDGenerator.createPeriodId(competitionId)
            periodDTO.setId(id)
        }

        val enrichedScheduleRequirements = periodsWithIds.flatMap { periodDTO ->
            // we need to process all schedule requirements to explicitly contain all the fight ids here.
            // if we have two schedule groups with specified fights for one category and they do not contain all the fights,
            // we will dynamically create a 'default' schedule group for the remaining fights
            // later if we move the schedule groups we will have to re-calculate the whole schedule again to avoid inconsistencies.
            periodDTO.scheduleRequirements.mapIndexed { index, it ->
                it.setId(it.id
                        ?: IDGenerator.scheduleRequirementId(competitionId, periodDTO.id, index, it.entryType)).setPeriodId(periodDTO.id)
            }
        }

        val flatFights = enrichedScheduleRequirements.flatMap { it.fightIds?.toList() ?: emptyList() }
        assert(flatFights.distinct().size == flatFights.size)
        val brackets = stages.filter { categoryCompetitorNumbers[it.first.b] ?: 0 > 0 }
                .map { p ->
                    val tuple3 = p.first
                    bracketSimulatorFactory.createSimulator(tuple3.a, tuple3.b, p.second,
                            tuple3.c, categoryCompetitorNumbers[tuple3.a] ?: 0)
                }

        val composer = ScheduleComposer(periods.map { p -> p.id!! to p.startTime!! }.toMap(), periods.flatMap {
            it.mats?.toList() ?: emptyList()
        },
                enrichedScheduleRequirements,
                brackets, periods.map { p -> p.id!! to BigDecimal(p.timeBetweenFights) }.toMap(), periods.map { p -> p.id!! to p.riskPercent }.toMap(), timeZone)

        val fightsByMats = composer.simulate().block(Duration.ofMillis(500)) ?: error("Generated schedule is null")

        return ScheduleDTO()
                .setId(competitionId)
                .setPeriods(periods.mapNotNull { period ->
                    PeriodDTO()
                            .setId(period.id)
                            .setRiskPercent(period.riskPercent)
                            .setTimeBetweenFights(period.timeBetweenFights)
                            .setIsActive(period.isActive)
                            .setScheduleRequirements(period.scheduleRequirements)
                            .setScheduleEntries(composer.schedule().sortedBy { it.startTime.toEpochMilli() }.mapIndexed { i, scheduleEntryDTO ->
                                scheduleEntryDTO.setId(IDGenerator.scheduleEntryId(competitionId, period.id, i, scheduleEntryDTO.entryType))
                                        .setOrder(i)
                            }.toTypedArray())
                            .setMats(fightsByMats.filter { it.periodId == period.id }.mapIndexed { i, container ->
                                MatDescriptionDTO()
                                        .setId(container.id)
                                        .setPeriodId(container.periodId)
                                        .setName(container.name)
                                        .setMatOrder(i)
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