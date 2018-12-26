package compman.compsrv.jpa.schedule

import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.model.dto.schedule.PeriodPropertiesDTO
import compman.compsrv.model.dto.schedule.SchedulePropertiesDTO
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.*
import javax.persistence.*

@Entity
data class PeriodProperties(@Id val id: String,
                            val startTime: ZonedDateTime,
                            val numberOfMats: Int,
                            val timeBetweenFights: Int,
                            val riskPercent: BigDecimal,
                            @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
                            @JoinColumn(name = "PERIOD_ID", nullable = true)
                            val categories: List<CategoryDescriptor>) {
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
        fun fromDTO(props: PeriodPropertiesDTO) = PeriodProperties(props.id, props.startTime, props.numberOfMats, props.timeBetweenFights, props.riskPercent, props.categories.map { CategoryDescriptor.fromDTO(it) })
    }
}

@Embeddable
@Access(AccessType.FIELD)
data class ScheduleProperties(val competitionId: String,
                              @OneToMany(orphanRemoval = true)
                              @JoinColumn(name = "SCHED_ID")
                              val periodPropertiesList: List<PeriodProperties>) {
    fun toDTO(): SchedulePropertiesDTO? {
        return SchedulePropertiesDTO()
                .setCompetitionId(competitionId)
                .setPeriodPropertiesList(periodPropertiesList.map { it.toDTO() }.toTypedArray())
    }

    companion object {
        fun fromDTO(dto: SchedulePropertiesDTO) =
                ScheduleProperties(dto.competitionId, dto.periodPropertiesList.map { PeriodProperties.fromDTO(it) })
    }
}