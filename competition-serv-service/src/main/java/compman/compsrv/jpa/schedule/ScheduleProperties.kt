package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.model.dto.schedule.PeriodPropertiesDTO
import compman.compsrv.model.dto.schedule.SchedulePropertiesDTO
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.*

@Entity
class PeriodProperties(id: String,
                       var startTime: Instant,
                       var numberOfMats: Int,
                       var timeBetweenFights: Int,
                       var riskPercent: BigDecimal,
                       @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
                       @JoinColumn(name = "PERIOD_ID", nullable = true)
                       var categories: List<CategoryDescriptor>) : AbstractJpaPersistable<String>(id) {
    fun toDTO(): PeriodPropertiesDTO {
        return PeriodPropertiesDTO()
                .setId(id)
                .setStartTime(startTime)
                .setNumberOfMats(numberOfMats)
                .setTimeBetweenFights(timeBetweenFights)
                .setRiskPercent(riskPercent)
                .setCategories(categories.map { it.toDTO() }.toTypedArray())
    }

    companion object {
        fun fromDTO(props: PeriodPropertiesDTO, competitionId: String) = PeriodProperties(props.id,
                props.startTime,
                props.numberOfMats,
                props.timeBetweenFights,
                props.riskPercent,
                props.categories.map { CategoryDescriptor.fromDTO(it, competitionId) })
    }
}

@Embeddable
@Access(AccessType.FIELD)
class ScheduleProperties(var id: String,
                         @OneToMany(orphanRemoval = true)
                         @JoinColumn(name = "SCHED_ID")
                         var periodPropertiesList: List<PeriodProperties>) {
    fun toDTO(): SchedulePropertiesDTO? {
        return SchedulePropertiesDTO()
                .setCompetitionId(id)
                .setPeriodPropertiesList(periodPropertiesList.map { it.toDTO() }.toTypedArray())
    }

    companion object {
        fun fromDTO(dto: SchedulePropertiesDTO) =
                ScheduleProperties(dto.competitionId, dto.periodPropertiesList.map { PeriodProperties.fromDTO(it, dto.competitionId) })
    }
}