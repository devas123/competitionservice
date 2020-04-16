package compman.compsrv.service.schedule

import com.compmanager.compservice.jooq.tables.pojos.CompScore
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
        fun obsoleteFight(f: FightDescription, cs: List<CompScore>, threeCompetitorCategory: Boolean = false): Boolean {
            if (threeCompetitorCategory) {
                return false
            }
            if (cs.any { !it.parentFightId.isNullOrBlank() }) return false
            return f.status == FightStatus.UNCOMPLETABLE.ordinal || f.status == FightStatus.WALKOVER.ordinal
        }

        fun getAllFightsParents(fightScores: List<CompScore>, getScoresForFight: (fightId: String) -> List<CompScore>?): List<String> {
            tailrec fun loop(result: List<String>, parentIds: List<String>): List<String> {
                return if (parentIds.isEmpty()) {
                    result
                } else {
                    loop(result + parentIds, parentIds.flatMap {
                        getScoresForFight(it)?.mapNotNull { compScore -> compScore.parentFightId }.orEmpty()
                    })
                }
            }
            return loop(emptyList(), fightScores.mapNotNull { it.parentFightId })
        }
    }

    /**
     * @param stages - Flux<pair<Tuple3<StageId, CategoryId, BracketType>, fights>>
     */
    fun generateSchedule(competitionId: String, periods: List<PeriodDTO>, mats: List<MatDescriptionDTO>, stages: Flux<StageGraph>, timeZone: String,
                         categoryCompetitorNumbers: Map<String, Int>, getFight: (fightId: String) -> List<CompScore>?): ScheduleDTO {
        if (!periods.isNullOrEmpty()) {
            return doGenerateSchedule(competitionId, stages, periods, mats, timeZone, getFight)
        } else {
            throw ServiceException("Periods are not specified!")
        }
    }

    private fun doGenerateSchedule(competitionId: String,
                                   stages: Flux<StageGraph>,
                                   periods: List<PeriodDTO>,
                                   mats: List<MatDescriptionDTO>,
                                   timeZone: String,
                                   getFight: (fightId: String) -> List<CompScore>?): ScheduleDTO {
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
        }.mapIndexed { _, it ->
            it.setId(it.id
                    ?: IDGenerator.scheduleRequirementId(competitionId, it.periodId, it.entryType))
        }.sortedBy { it.entryOrder }

        val flatFights = enrichedScheduleRequirements.flatMap { it.fightIds?.toList().orEmpty() }
        assert(flatFights.distinct().size == flatFights.size)

        val composer = ScheduleProducer(startTime = periods.map { p -> p.id!! to p.startTime!! }.toMap(),
                mats = mats,
                req = enrichedScheduleRequirements,
                brackets = stages,
                timeBetweenFights = periods.map { p -> p.id!! to BigDecimal(p.timeBetweenFights) }.toMap(),
                riskFactor = periods.map { p -> p.id!! to p.riskPercent }.toMap(),
                timeZone = timeZone,
                getFightScores = getFight)

        val fightsByMats = composer.simulate().block(Duration.ofMillis(500)) ?: error("Generated schedule is null")
        val invalidFightIds = fightsByMats.c

        return ScheduleDTO()
                .setId(competitionId)
                .setMats(fightsByMats.b.mapIndexed { i, container ->
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
                            .setStartTime(period.startTime)
                            .setName(period.name)
                }.toTypedArray())
    }
}