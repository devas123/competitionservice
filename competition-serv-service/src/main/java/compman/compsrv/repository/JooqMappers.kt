package compman.compsrv.repository

import arrow.core.Tuple2
import arrow.core.Tuple3
import com.compmanager.compservice.jooq.tables.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.FightStartTimePairDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.model.dto.schedule.ScheduleEntryDTO
import compman.compsrv.model.dto.schedule.ScheduleEntryType
import org.jooq.Record
import org.springframework.stereotype.Component
import reactor.core.publisher.GroupedFlux
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Supplier
import java.util.stream.Collector

@Component
class JooqMappers {

    fun hasFightStartTime(u: Record): Boolean {
        return (!u[FightDescription.FIGHT_DESCRIPTION.ID].isNullOrBlank()
                && !u[FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID].isNullOrBlank() &&
                u[FightDescription.FIGHT_DESCRIPTION.START_TIME] != null)
    }

    fun fightStartTimePairDTO(u: Record): FightStartTimePairDTO {
        return FightStartTimePairDTO()
                .setMatId(u[FightDescription.FIGHT_DESCRIPTION.MAT_ID])
                .setFightCategoryId(u[FightDescription.FIGHT_DESCRIPTION.CATEGORY_ID])
                .setPeriodId(u[FightDescription.FIGHT_DESCRIPTION.PERIOD])
                .setStartTime(u[FightDescription.FIGHT_DESCRIPTION.START_TIME]?.toInstant())
                .setNumberOnMat(u[FightDescription.FIGHT_DESCRIPTION.NUMBER_ON_MAT])
    }


    fun periodCollector(rec: GroupedFlux<String, Record>): Collector<Record, Tuple3<PeriodDTO, MutableList<Tuple2<MatDescriptionDTO, MutableList<FightStartTimePairDTO>>>, MutableList<Tuple3<ScheduleEntryDTO, MutableList<String>, MutableList<String>>>>, Tuple3<PeriodDTO, MutableList<Tuple2<MatDescriptionDTO, MutableList<FightStartTimePairDTO>>>, MutableList<Tuple3<ScheduleEntryDTO, MutableList<String>, MutableList<String>>>>> {
        return Collector.of(Supplier {
            Tuple3(PeriodDTO().setId(rec.key()),
                    mutableListOf<Tuple2<MatDescriptionDTO, MutableList<FightStartTimePairDTO>>>(),
                    mutableListOf<Tuple3<ScheduleEntryDTO, MutableList<String>,
                            MutableList<String>>>())
        },
                BiConsumer<Tuple3<PeriodDTO,
                        MutableList<Tuple2<MatDescriptionDTO, MutableList<FightStartTimePairDTO>>>,
                        MutableList<Tuple3<ScheduleEntryDTO, MutableList<String>, MutableList<String>>>>, Record> { t, u ->
                    t.a
                            .setEndTime(u[SchedulePeriod.SCHEDULE_PERIOD.END_TIME]?.toInstant())
                            .setIsActive(u[SchedulePeriod.SCHEDULE_PERIOD.IS_ACTIVE])
                            .setRiskPercent(u[SchedulePeriod.SCHEDULE_PERIOD.RISK_PERCENT])
                            .setName(u[SchedulePeriod.SCHEDULE_PERIOD.NAME])
                            .setStartTime(u[SchedulePeriod.SCHEDULE_PERIOD.START_TIME]?.toInstant())
                            .setTimeBetweenFights(u[SchedulePeriod.SCHEDULE_PERIOD.TIME_BETWEEN_FIGHTS])
                    if (!u[MatDescription.MAT_DESCRIPTION.ID].isNullOrBlank()) {
                        if (t.b.any { u[MatDescription.MAT_DESCRIPTION.ID] == it.a.id }) {
                            if (hasFightStartTime(u)) {
                                t.b.first { u[MatDescription.MAT_DESCRIPTION.ID] == it.a.id }.b.add(fightStartTimePairDTO(u))
                            }
                        } else {
                            if (hasFightStartTime(u)) {
                                t.b.add(Tuple2(MatDescriptionDTO(), mutableListOf(fightStartTimePairDTO(u))))
                            } else {
                                t.b.add(Tuple2(MatDescriptionDTO(), mutableListOf()))
                            }
                        }
                    }
                    if (!u[ScheduleEntry.SCHEDULE_ENTRY.ID].isNullOrBlank()) {
                        if (t.c.none { tc -> tc.a.id == u[ScheduleEntry.SCHEDULE_ENTRY.ID] }) {
                            t.c.add(Tuple3(
                                    scheduleEntryDTO(u)
                                    , mutableListOf(), mutableListOf()))
                        }
                        val updatable = t.c.first { tc -> tc.a.id == u[ScheduleEntry.SCHEDULE_ENTRY.ID] }
                        if (!u[FightDescription.FIGHT_DESCRIPTION.SCHEDULE_ENTRY_ID].isNullOrBlank()) {
                            updatable.b.add(u[FightDescription.FIGHT_DESCRIPTION.ID])
                        }
                        if (u[CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.SCHEDULE_ENTRY_ID] == updatable.a.id &&
                                !u[CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.CATEGORY_ID].isNullOrBlank()) {
                            updatable.c.add(u[CategoryScheduleEntry.CATEGORY_SCHEDULE_ENTRY.CATEGORY_ID])
                        }
                    }
                }, BinaryOperator { t, u ->
            if (t.a.id == u.a.id) {
                u.b.forEach { m ->
                    if (t.b.any { bm -> bm.a.id == m.a.id }) {
                        t.b.first { bm -> bm.a.id == m.a.id }.b.addAll(m.b)
                    } else {
                        t.b.add(m)
                    }
                }
                u.c.forEach { uc ->
                    if (t.c.any { it.a.id == uc.a.id }) {
                        t.c.first { it.a.id == uc.a.id }.b.addAll(uc.b)
                        t.c.first { it.a.id == uc.a.id }.c.addAll(uc.c)
                    } else {
                        t.c.add(uc)
                    }
                }
                t
            } else {
                throw RuntimeException("Periods with different ids... $t\n $u")
            }
        }, Collector.Characteristics.IDENTITY_FINISH, Collector.Characteristics.CONCURRENT)
    }

    fun scheduleEntryDTO(u: Record): ScheduleEntryDTO {
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
    }

}