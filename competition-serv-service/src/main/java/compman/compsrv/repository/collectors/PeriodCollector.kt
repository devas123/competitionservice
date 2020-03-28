package compman.compsrv.repository.collectors

import com.compmanager.compservice.jooq.tables.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.util.orFalse
import org.jooq.Record
import java.util.*
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector

class ScheduleEntryAccumulator(private val scheduleEntry: ScheduleEntryDTO) {
    fun getId(): String = scheduleEntry.id
    val invalidFightIds = mutableSetOf<String>()
    val categoryIds = mutableSetOf<String>()
    val fightIds = mutableSetOf<String>()
    fun getScheduleEntry() = scheduleEntry.setInvalidFightIds(invalidFightIds.toTypedArray()).setCategoryIds(categoryIds.toTypedArray()).setFightIds(fightIds.toTypedArray())
}

class ScheduleRequirementAccumulator(private val scheduleRequirementDTO: ScheduleRequirementDTO) {
    fun getId(): String = scheduleRequirementDTO.id
    val categoryIds = mutableSetOf<String>()
    val fightIds = mutableSetOf<String>()
    fun getScheduleRequirement(): ScheduleRequirementDTO = scheduleRequirementDTO.setCategoryIds(categoryIds.toTypedArray()).setFightIds(fightIds.toTypedArray())
}

class MatDescriptionAccumulator(private val matDescriptionDTO: MatDescriptionDTO) {
    fun getId(): String = matDescriptionDTO.id

    val fightStartTimes = TreeSet(Comparator.comparing { t: FightStartTimePairDTO -> t.fightId })

    fun getMat(): MatDescriptionDTO = matDescriptionDTO.setFightStartTimes(fightStartTimes.toTypedArray())
}

class PeriodAccumulator(val periodDTO: PeriodDTO) {
    fun getId(): String = periodDTO.id
    val matAccumulators = mutableListOf<MatDescriptionAccumulator>()
    val scheduleEntryAccumulators = mutableListOf<ScheduleEntryAccumulator>()
    val scheduleRequirementAccumulators = mutableListOf<ScheduleRequirementAccumulator>()
}


class PeriodCollector(private val periodId: String) : Collector<Record,
        PeriodAccumulator,
        PeriodDTO> {

    private fun hasFightStartTime(u: Record): Boolean {
        return (!u[FightDescription.FIGHT_DESCRIPTION.ID].isNullOrBlank()
                && !u[FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID].isNullOrBlank() &&
                u[FightDescription.FIGHT_DESCRIPTION.START_TIME] != null
                && u[FightDescription.FIGHT_DESCRIPTION.MAT_ID] == u[MatDescription.MAT_DESCRIPTION.ID])
    }

    private fun matDescriptionDTO(u: Record): MatDescriptionDTO {
        return MatDescriptionDTO().setId(u[MatDescription.MAT_DESCRIPTION.ID])
                .setMatOrder(u[MatDescription.MAT_DESCRIPTION.MAT_ORDER])
                .setPeriodId(u[MatDescription.MAT_DESCRIPTION.PERIOD_ID])
                .setName(u[MatDescription.MAT_DESCRIPTION.NAME])
                .setFightStartTimes(emptyArray())
    }

    private fun scheduleEntryDTO(u: Record): ScheduleEntryDTO {
        return ScheduleEntryDTO()
                .setOrder(u[ScheduleEntry.SCHEDULE_ENTRY.SCHEDULE_ORDER])
                .setEntryType(u[ScheduleEntry.SCHEDULE_ENTRY.ENTRY_TYPE]?.let { ScheduleEntryType.values()[it] })
                .setEndTime(u[ScheduleEntry.SCHEDULE_ENTRY.END_TIME]?.toInstant())
                .setStartTime(u[ScheduleEntry.SCHEDULE_ENTRY.START_TIME]?.toInstant())
                .setMatId(u[ScheduleEntry.SCHEDULE_ENTRY.MAT_ID])
                .setDescription(u[ScheduleEntry.SCHEDULE_ENTRY.DESCRIPTION])
                .setDuration(u[ScheduleEntry.SCHEDULE_ENTRY.DURATION])
                .setId(u[ScheduleEntry.SCHEDULE_ENTRY.ID])
                .setPeriodId(u[ScheduleEntry.SCHEDULE_ENTRY.PERIOD_ID])
                .setName(u[ScheduleEntry.SCHEDULE_ENTRY.NAME])
                .setColor(u[ScheduleEntry.SCHEDULE_ENTRY.COLOR])
    }

    private fun scheduleRequirement(u: Record): ScheduleRequirementDTO {
        return ScheduleRequirementDTO()
                .setEntryType(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.ENTRY_TYPE]?.let { ScheduleRequirementType.values()[it] })
                .setEndTime(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.END_TIME]?.toInstant())
                .setStartTime(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.START_TIME]?.toInstant())
                .setMatId(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.MAT_ID])
                .setId(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.ID])
                .setForce(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.FORCE])
                .setPeriodId(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.PERIOD_ID])
                .setDurationMinutes(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.DURATION_MINUTES])
                .setEntryOrder(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.ENTRY_ORDER])
                .setName(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.NAME])
                .setColor(u[ScheduleRequirement.SCHEDULE_REQUIREMENT.COLOR])

    }

    private fun fightStartTimePairDTO(u: Record): FightStartTimePairDTO {
        return FightStartTimePairDTO()
                .setFightId(u[FightDescription.FIGHT_DESCRIPTION.ID])
                .setMatId(u[FightDescription.FIGHT_DESCRIPTION.MAT_ID])
                .setFightCategoryId(u[FightDescription.FIGHT_DESCRIPTION.CATEGORY_ID])
                .setPeriodId(u[FightDescription.FIGHT_DESCRIPTION.PERIOD])
                .setStartTime(u[FightDescription.FIGHT_DESCRIPTION.START_TIME]?.toInstant())
                .setNumberOnMat(u[FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT])
                .setInvalid(u[FightDescription.FIGHT_DESCRIPTION.INVALID])
    }


    override fun characteristics(): MutableSet<Collector.Characteristics> {
        return mutableSetOf(Collector.Characteristics.CONCURRENT)
    }

    override fun supplier(): Supplier<PeriodAccumulator> {
        return Supplier {
            PeriodAccumulator(PeriodDTO()
                    .setId(periodId))
        }
    }

    override fun accumulator(): BiConsumer<PeriodAccumulator, Record> {
        return BiConsumer { t, u ->
            t.periodDTO.apply {
                endTime = (u[SchedulePeriod.SCHEDULE_PERIOD.END_TIME]?.toInstant())
                isActive = (u[SchedulePeriod.SCHEDULE_PERIOD.IS_ACTIVE])
                riskPercent = (u[SchedulePeriod.SCHEDULE_PERIOD.RISK_PERCENT])
                name = (u[SchedulePeriod.SCHEDULE_PERIOD.NAME])
                startTime = (u[SchedulePeriod.SCHEDULE_PERIOD.START_TIME]?.toInstant())
                timeBetweenFights = (u[SchedulePeriod.SCHEDULE_PERIOD.TIME_BETWEEN_FIGHTS])
            }
            if (!u[MatDescription.MAT_DESCRIPTION.ID].isNullOrBlank()) {
                if (t.matAccumulators.any { u[MatDescription.MAT_DESCRIPTION.ID] == it.getId() }) {
                    if (hasFightStartTime(u)) {
                        t.matAccumulators.first { u[MatDescription.MAT_DESCRIPTION.ID] == it.getId() }.fightStartTimes.add(fightStartTimePairDTO(u))
                    }
                } else {
                    t.matAccumulators.add(MatDescriptionAccumulator(matDescriptionDTO(u)).apply {
                        if (hasFightStartTime(u)) {
                            fightStartTimes.add(fightStartTimePairDTO(u))
                        }
                    })
                }
            }
            if (!u[ScheduleEntry.SCHEDULE_ENTRY.ID].isNullOrBlank()) {
                if (t.scheduleEntryAccumulators.none { tc ->
                            tc.getId() == u[ScheduleEntry.SCHEDULE_ENTRY.ID]
                        }) {
                    t.scheduleEntryAccumulators.add(ScheduleEntryAccumulator(scheduleEntryDTO(u)))
                }
                val updatable = t.scheduleEntryAccumulators.first { tc -> tc.getId() == u[ScheduleEntry.SCHEDULE_ENTRY.ID] }
                if (!u[FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID].isNullOrBlank()) {
                    val id = u[FightDescription.FIGHT_DESCRIPTION.ID]
                    updatable.fightIds.add(id)
                    if (u[FightDescription.FIGHT_DESCRIPTION.INVALID].orFalse()) {
                        updatable.invalidFightIds.add(id)
                    }
                }
                if (u[CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.SCHEDULE_ENTRY_ID] == updatable.getId() &&
                        !u[CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.CATEGORY_ID].isNullOrBlank()) {
                    updatable.categoryIds.add(u[CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.CATEGORY_ID])
                }
            }
            if (!u[ScheduleRequirement.SCHEDULE_REQUIREMENT.ID].isNullOrBlank()) {
                if (t.scheduleRequirementAccumulators.none { tc -> tc.getId() == u[ScheduleRequirement.SCHEDULE_REQUIREMENT.ID] }) {
                    t.scheduleRequirementAccumulators.add(ScheduleRequirementAccumulator(scheduleRequirement(u)))
                }
            }
        }
    }

    override fun combiner(): BinaryOperator<PeriodAccumulator> {
        return BinaryOperator { t, u ->
            if (t.periodDTO.id == u.periodDTO.id) {
                u.matAccumulators.forEach { m ->
                    if (t.matAccumulators.any { bm -> bm.getId() == m.getId() }) {
                        t.matAccumulators.first { bm -> bm.getId() == m.getId() }.fightStartTimes.addAll(m.fightStartTimes)
                    } else {
                        t.matAccumulators.add(m)
                    }
                }
                u.scheduleEntryAccumulators.forEach { uc ->
                    if (t.scheduleEntryAccumulators.any { it.getId() == uc.getId() }) {
                        t.scheduleEntryAccumulators.first { it.getId() == uc.getId() }.fightIds.addAll(uc.fightIds)
                        t.scheduleEntryAccumulators.first { it.getId() == uc.getId() }.categoryIds.addAll(uc.categoryIds)
                        t.scheduleEntryAccumulators.first { it.getId() == uc.getId() }.invalidFightIds.addAll(uc.invalidFightIds)
                    } else {
                        t.scheduleEntryAccumulators.add(uc)
                    }
                }
                t
            } else {
                throw RuntimeException("Periods with different ids... $t\n $u")
            }
        }
    }

    override fun finisher(): Function<PeriodAccumulator, PeriodDTO> {
        return Function { t ->
            t.periodDTO
                    .setMats(t.matAccumulators.map { tm -> tm.getMat() }.toTypedArray())
                    .setScheduleEntries(t.scheduleEntryAccumulators.map { tm ->
                        tm.getScheduleEntry()
                    }.toTypedArray())
                    .setScheduleRequirements(t.scheduleRequirementAccumulators.map { it.getScheduleRequirement() }.toTypedArray())

        }
    }
}