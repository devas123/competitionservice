package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.model.dto.schedule.PeriodDTO
import java.time.ZonedDateTime
import javax.persistence.*

@Entity
data class Period(@Id val id: String,
                  val name: String,
                  @ElementCollection
                  @CollectionTable(
                          name = "SCHEDULE_ENTRIES",
                          joinColumns = [JoinColumn(name = "PERIOD_ID")]
                  )
                  val schedule: List<ScheduleEntry>,
                  @OneToMany(orphanRemoval = true)
                  @JoinColumn(name="PERIOD_ID", nullable = true)
                  val categories: List<CategoryDescriptor>,
                  val startTime: ZonedDateTime,
                  val numberOfMats: Int,
                  @OneToMany(orphanRemoval = true)
                  @JoinColumn(name="PERIOD_ID", nullable = false)
                  val fightsByMats: List<MatScheduleContainer>?) {
    fun toDTO(): PeriodDTO? {
        return PeriodDTO()
                .setId(id)
                .setName(name)
                .setSchedule(schedule.map { it.toDTO() }.toTypedArray())
                .setCategories(categories.map { it.toDTO() }.toTypedArray())
                .setStartTime(startTime)
                .setNumberOfMats(numberOfMats)
                .setFightsByMats(fightsByMats?.map { it.toDTO() }?.toTypedArray())
    }

    companion object {
        fun fromDTO(dto: PeriodDTO) = Period(
                id = dto.id,
                name = dto.name,
                schedule = dto.schedule.map { ScheduleEntry.fromDTO(it) },
                categories = dto.categories.map { CategoryDescriptor.fromDTO(it) },
                startTime = dto.startTime,
                numberOfMats = dto.numberOfMats,
                fightsByMats = dto.fightsByMats.map { MatScheduleContainer.fromDTO(it) }
        )
    }
}