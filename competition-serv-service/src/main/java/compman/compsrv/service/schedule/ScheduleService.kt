package compman.compsrv.service.schedule

import arrow.core.Tuple2
import arrow.core.toT
import com.compmanager.service.ServiceException
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.util.IDGenerator
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class ScheduleService {

    companion object {
        fun obsoleteFight(f: FightDescriptionDTO, cs: List<CompScoreDTO>, threeCompetitorCategory: Boolean = false): Boolean {
            if (threeCompetitorCategory) {
                return false
            }
            if (cs.any { !it.parentFightId.isNullOrBlank() }) return false
            return f.status == FightStatus.UNCOMPLETABLE || f.status == FightStatus.WALKOVER
        }

    }

    /**
     * @param stages - Flux<pair<Tuple3<StageId, CategoryId, BracketType>, fights>>
     */
    fun generateSchedule(competitionId: String, periods: List<PeriodDTO>, mats: List<MatDescriptionDTO>, stages: StageGraph, timeZone: String,
                         categoryCompetitorNumbers: Map<String, Int>): Tuple2<ScheduleDTO, List<FightStartTimePairDTO>> {
        return if (!periods.isNullOrEmpty()) {
            doGenerateSchedule(competitionId, stages, periods, mats, timeZone)
        } else {
            throw ServiceException("Periods are not specified!")
        }
    }

    private fun doGenerateSchedule(competitionId: String,
                                   stages: StageGraph,
                                   periods: List<PeriodDTO>,
                                   mats: List<MatDescriptionDTO>,
                                   timeZone: String): Tuple2<ScheduleDTO, List<FightStartTimePairDTO>> {
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

        val composer = ScheduleProducer(competitionId = competitionId,
                startTime = periods.map { p -> p.id!! to p.startTime!! }.toMap(),
                mats = mats,
                req = enrichedScheduleRequirements,
                stages = stages,
                periods = periods.sortedBy { it.startTime.toEpochMilli() },
                timeBetweenFights = periods.map { p -> p.id!! to BigDecimal(p.timeBetweenFights) }.toMap(),
                riskFactor = periods.map { p -> p.id!! to p.riskPercent }.toMap(),
                timeZone = timeZone)

        return composer.simulate().let { fightsByMats ->
            val invalidFightIds = fightsByMats.c
            ScheduleDTO()
                .setId(competitionId)
                .setMats(fightsByMats.b.mapIndexed { i, container ->
                    MatDescriptionDTO()
                        .setId(container.id)
                        .setPeriodId(container.periodId)
                        .setName(container.name)
                        .setMatOrder(container.matOrder ?: i)
                        .setNumberOfFights(container.fights.size)
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
                                    .setFightIds(scheduleEntryDTO.fightIds?.distinctBy { it.someId }?.toTypedArray())
                                    .setCategoryIds(scheduleEntryDTO.categoryIds?.distinct()?.toTypedArray())
                                    .setOrder(i)
                                    .setInvalidFightIds(scheduleEntryDTO.fightIds?.filter { invalidFightIds.contains(it.someId) }
                                        ?.mapNotNull { it.someId }
                                        ?.distinct()?.toTypedArray())
                            }.toTypedArray())
                        .setStartTime(period.startTime)
                        .setName(period.name)
                }.toTypedArray()) toT fightsByMats.b.flatMap { container ->
                container.fights.map {
                    FightStartTimePairDTO()
                        .setStartTime(it.startTime)
                        .setNumberOnMat(it.fightNumber)
                        .setFightId(it.fightId)
                        .setPeriodId(it.periodId)
                        .setFightCategoryId(it.categoryId)
                        .setMatId(it.matId)
                        .setScheduleEntryId(it.scheduleEntryId)
                }
            }
        }
    }
}