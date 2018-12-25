package compman.compsrv.jpa.competition

import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.model.dto.competition.CompetitionStateDTO
import compman.compsrv.model.dto.competition.CompetitionStatus
import java.io.Serializable
import javax.persistence.*

@Entity
@Table(name = "competition_state")
data class CompetitionState constructor(
        @Id val competitionId: String,
        @OneToMany(orphanRemoval = true)
        @JoinColumn(name = "category_id")
        val categories: List<CategoryState>,
        @OneToOne
        @MapsId
        val properties: CompetitionProperties,
        @OneToOne
        @MapsId
        val schedule: Schedule?,
        @OneToOne
        @MapsId
        val dashboardState: CompetitionDashboardState,
        val status: CompetitionStatus) : Serializable {

    companion object {
        fun fromDTO(dto: CompetitionStateDTO): CompetitionState {
            val properties = CompetitionProperties.fromDTO(dto.properties)
            return CompetitionState(
                    competitionId = dto.competitionId,
                    categories = dto.categories.map { CategoryState.fromDTO(it, properties) },
                    properties = properties,
                    schedule = dto.schedule?.let { Schedule.fromDTO(it) },
                    dashboardState = CompetitionDashboardState.fromDTO(dto.dashboardState),
                    status = dto.status
            )
        }
    }

    @Version
    var version: Long = 0
        private set

    constructor(competitionId: String, properties: CompetitionProperties) : this(
            competitionId = competitionId,
            properties = properties,
            categories = emptyList(),
            schedule = null,
            dashboardState = CompetitionDashboardState(competitionId, emptySet()),
            status = CompetitionStatus.CREATED)


    fun withStatus(status: CompetitionStatus) = copy(status = status)

}