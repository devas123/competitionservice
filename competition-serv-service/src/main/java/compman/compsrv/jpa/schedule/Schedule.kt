package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.repository.FightCrudRepository
import java.math.BigDecimal
import java.math.RoundingMode
import javax.persistence.*

@Entity
class Schedule(id: String,
               @Embedded
               @AttributeOverrides(
                       AttributeOverride(name = "id", column = Column(name = "properties_id"))
               )
               var scheduleProperties: ScheduleProperties?,
               @OneToMany(orphanRemoval = true, cascade = [CascadeType.ALL])
               @JoinColumn(name = "SCHED_ID")
               var periods: List<Period>?) : AbstractJpaPersistable<String>(id) {
    fun toDTO(): ScheduleDTO? {
        return ScheduleDTO()
                .setId(id)
                .setPeriods(periods?.map { it.toDTO() }?.toTypedArray())
                .setScheduleProperties(scheduleProperties?.toDTO())
    }

    companion object {
        fun fromDTO(dto: ScheduleDTO, fightCrudRepository: FightCrudRepository) = Schedule(
                id = dto.id,
                scheduleProperties = ScheduleProperties.fromDTO(dto.scheduleProperties),
                periods = dto.periods.map { Period.fromDTO(it, dto.scheduleProperties.competitionId, fightCrudRepository) }
        )

        fun fromDTO(dto: ScheduleDTO, fights: List<FightDescription>) = Schedule(
                id = dto.id,
                scheduleProperties = ScheduleProperties.fromDTO(dto.scheduleProperties),
                periods = dto.periods.map { Period.fromDTO(it, dto.scheduleProperties.competitionId, fights) }
        )

        fun compNotEmpty(comp: Competitor?): Boolean {
            if (comp == null) return false
            val firstName = comp.firstName
            val lastName = comp.lastName
            return firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty()
        }

        fun getPeriodDuration(period: Period): BigDecimal? {
            val startTime = period.startTime.toEpochMilli()
            val endTime = period.fightsByMats?.map { it.currentTime }?.sortedBy { it.toEpochMilli() }?.lastOrNull()?.toEpochMilli()
                    ?: startTime
            val durationMillis = endTime - startTime
            if (durationMillis > 0) {
                return BigDecimal.valueOf(durationMillis).divide(BigDecimal(1000 * 60 * 60), 2, RoundingMode.HALF_UP)
            }
            return null
        }

    }
}
