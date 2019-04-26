package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.jpa.schedule.ScheduleProperties
import compman.compsrv.model.dto.competition.CompetitionStateDTO
import compman.compsrv.model.dto.competition.CompetitionStatus
import java.io.Serializable
import javax.persistence.*

@Entity(name = "competition_state")
@Table(name = "competition_state")
class CompetitionState(id: String,
                       @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "competition")
                       var categories: List<CategoryState>,
                       @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
                       @PrimaryKeyJoinColumn
                       var properties: CompetitionProperties? = null,
                       @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
                       @PrimaryKeyJoinColumn
                       var schedule: Schedule? = null,
                       @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
                       @PrimaryKeyJoinColumn
                       var dashboardState: CompetitionDashboardState? = null,
                       var status: CompetitionStatus) : AbstractJpaPersistable<String>(id), Serializable {

    companion object {
        fun fromDTO(dto: CompetitionStateDTO): CompetitionState {
            val properties = CompetitionProperties.fromDTO(dto.properties)
            val fights = dto.categories?.flatMap { it.brackets?.fights?.toList() ?: emptyList() }?.map { FightDescription.fromDTO(it) } ?: emptyList()
            val fakeState = CompetitionState(
                    id = dto.competitionId,
                    categories = emptyList(),
                    properties = properties,
                    schedule = dto.schedule?.let { Schedule.fromDTO(it, fights) }
                            ?: Schedule(dto.competitionId, ScheduleProperties(dto.competitionId, emptyList()), emptyList()),
                    dashboardState = dto.dashboardState?.let { CompetitionDashboardState.fromDTO(it) }
                            ?: CompetitionDashboardState(dto.competitionId, emptySet()),
                    status = dto.status
            )
            return CompetitionState(
                    id = dto.competitionId,
                    categories = dto.categories.map { CategoryState.fromDTO(it, fakeState) },
                    properties = properties,
                    schedule = dto.schedule?.let { Schedule.fromDTO(it, fights) }
                            ?: Schedule(dto.competitionId, ScheduleProperties(dto.competitionId, emptyList()), emptyList()),
                    dashboardState = dto.dashboardState?.let { CompetitionDashboardState.fromDTO(it) }
                            ?: CompetitionDashboardState(dto.competitionId, emptySet()),
                    status = dto.status
            )
        }
    }

    @Version
    var version: Long = 0
        private set

    constructor(competitionId: String, properties: CompetitionProperties) : this(
            id = competitionId,
            properties = properties,
            categories = emptyList(),
            schedule = Schedule(competitionId, ScheduleProperties(competitionId, emptyList()), emptyList()),
            dashboardState = CompetitionDashboardState(competitionId, emptySet()),
            status = CompetitionStatus.CREATED)


    fun withStatus(status: CompetitionStatus): CompetitionState {
        this.status = status
        return this
    }

    fun toDTO(includeCompetitors: Boolean = false, includeBrackets: Boolean = false): CompetitionStateDTO = CompetitionStateDTO()
            .setCategories(categories.map { it.toDTO(includeCompetitors, includeBrackets) }.toTypedArray())
            .setCompetitionId(id)
            .setDashboardState(dashboardState?.toDTO())
            .setProperties(properties?.toDTO())
            .setSchedule(schedule?.toDTO())
            .setStatus(status)
}