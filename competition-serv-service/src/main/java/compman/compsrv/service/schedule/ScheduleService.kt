package compman.compsrv.service.schedule

import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import com.compmanager.service.ServiceException
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.util.IDGenerator
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.time.Duration

@Component
class ScheduleService {

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
    }

    /**
     * @param stages - Flux<pair<Tuple3<StageId, CategoryId, BracketType>, fights>>
     */
    fun generateSchedule(competitionId: String, periods: List<PeriodDTO>, stages: Flux<StageGraph>, timeZone: String,
                         categoryCompetitorNumbers: Map<String, Int>, getFight: (fightId: String) -> FightDescription?): ScheduleDTO {
        if (!periods.isNullOrEmpty()) {
            return doGenerateSchedule(competitionId, stages, periods, timeZone, getFight)
        } else {
            throw ServiceException("Periods are not specified!")
        }
    }

    private fun doGenerateSchedule(competitionId: String,
                                   stages: Flux<StageGraph>,
                                   periods: List<PeriodDTO>,
                                   timeZone: String,
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
            periodDTO.scheduleRequirements?.map { it.setPeriodId(periodDTO.id) }.orEmpty()
        }.mapIndexed { index, it ->
            it.setId(it.id
                    ?: IDGenerator.scheduleRequirementId(competitionId, it.periodId, it.entryType))
        }.sortedBy { it.entryOrder }

        val flatFights = enrichedScheduleRequirements.flatMap { it.fightIds?.toList().orEmpty() }
        assert(flatFights.distinct().size == flatFights.size)

        val composer = ScheduleProducer(startTime = periods.map { p -> p.id!! to p.startTime!! }.toMap(),
                mats = periods.flatMap { it.mats?.toList().orEmpty() },
                req = enrichedScheduleRequirements,
                brackets = stages,
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
                            .setScheduleRequirements(enrichedScheduleRequirements.filter { it.periodId === period.id }.toTypedArray())
                            .setScheduleEntries(fightsByMats.a.filter { it.periodId == period.id }
                                    .sortedBy { it.startTime.toEpochMilli() }
                                    .mapIndexed { i, scheduleEntryDTO ->
                                        scheduleEntryDTO
                                                .setId(IDGenerator
                                                        .scheduleEntryId(competitionId, period.id))
                                                .setOrder(i)
                                                .setInvalidFightIds(scheduleEntryDTO.fightIds?.filter { invalidFightIds.contains(it.someId) }
                                                        ?.mapNotNull { it.someId }
                                                        ?.distinct()?.toTypedArray())
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