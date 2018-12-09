package compman.compsrv.model.competition

import compman.compsrv.model.schedule.Schedule
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
        val status: CompetitionStatus) {

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

    fun withCompetitionId(competitionId: String) = if (competitionId.isNotBlank()) copy(competitionId = competitionId) else this

    fun withStatus(status: CompetitionStatus) = copy(status = status)

    fun withSchedule(schedule: Schedule?) = copy(schedule = schedule)

    fun withDashboardState(dashboardState: CompetitionDashboardState) = copy(dashboardState = dashboardState)

    fun withProperties(properties: CompetitionProperties) = copy(properties = properties)

    fun withCategoryAdded(categoryState: CategoryState) = copy(categories = categories + categoryState)
    fun withCategoryRemoved(categoryId: String) = copy(categories = categories.filter { it.id != categoryId })
}