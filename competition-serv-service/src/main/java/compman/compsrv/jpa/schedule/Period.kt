package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.repository.FightCrudRepository
import java.time.Instant
import javax.persistence.*

@Entity
class Period(id: String,
             var name: String,
             @ElementCollection
             @CollectionTable(
                     name = "SCHEDULE_ENTRIES",
                     joinColumns = [JoinColumn(name = "PERIOD_ID")]
             )
             var schedule: List<ScheduleEntry>,
             @OneToMany(fetch = FetchType.LAZY)
             @JoinColumn(name = "PERIOD_ID", nullable = true)
             var categories: List<CategoryDescriptor>,
             var startTime: Instant,
             var numberOfMats: Int,
             @OneToMany(orphanRemoval = true, cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
             @JoinColumn(name = "PERIOD_ID", nullable = false)
             var fightsByMats: List<MatScheduleContainer>?) : AbstractJpaPersistable<String>(id) {
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
        fun fromDTO(dto: PeriodDTO, competitionId: String, fightCrudRepository: FightCrudRepository) = Period(
                id = dto.id,
                name = dto.name,
                schedule = dto.schedule.map { ScheduleEntry.fromDTO(it) },
                categories = dto.categories.map { CategoryDescriptor.fromDTO(it, competitionId) },
                startTime = dto.startTime,
                numberOfMats = dto.numberOfMats,
                fightsByMats = dto.fightsByMats?.map { MatScheduleContainer.fromDTO(it, fightCrudRepository) }
        )

        fun fromDTO(dto: PeriodDTO, competitionId: String, fights: List<FightDescription>) = Period(
                id = dto.id,
                name = dto.name,
                schedule = dto.schedule.map { ScheduleEntry.fromDTO(it) },
                categories = dto.categories.map { CategoryDescriptor.fromDTO(it, competitionId) },
                startTime = dto.startTime,
                numberOfMats = dto.numberOfMats,
                fightsByMats = dto.fightsByMats?.map { MatScheduleContainer.fromDTO(it, fights) }
        )
    }
}