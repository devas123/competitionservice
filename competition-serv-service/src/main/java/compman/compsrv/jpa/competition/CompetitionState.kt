package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.dashboard.CompetitionDashboardState
import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.jpa.schedule.ScheduleProperties
import compman.compsrv.model.dto.competition.CompetitionStatus
import org.hibernate.annotations.Cascade
import java.io.Serializable
import javax.persistence.*

@Entity(name = "competition_state")
class CompetitionState(id: String,
                       @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
                       @Cascade(org.hibernate.annotations.CascadeType.ALL)
                       @JoinColumn(name = "competition_id", nullable = false)
                       var categories: MutableSet<CategoryState>,
                       @OneToOne(fetch = FetchType.LAZY, orphanRemoval = true)
                       @Cascade(org.hibernate.annotations.CascadeType.ALL)
                       @PrimaryKeyJoinColumn
                       var properties: CompetitionProperties? = null,
                       @OneToOne(fetch = FetchType.LAZY, orphanRemoval = true)
                       @Cascade(org.hibernate.annotations.CascadeType.ALL)
                       @PrimaryKeyJoinColumn
                       var schedule: Schedule? = null,
                       @OneToOne(fetch = FetchType.LAZY, orphanRemoval = true)
                       @Cascade(org.hibernate.annotations.CascadeType.ALL)
                       @PrimaryKeyJoinColumn
                       var dashboardState: CompetitionDashboardState?,
                       var status: CompetitionStatus) : AbstractJpaPersistable<String>(id), Serializable {

    constructor(competitionId: String, properties: CompetitionProperties) : this(
            id = competitionId,
            properties = properties,
            categories = mutableSetOf(),
            schedule = Schedule(competitionId, ScheduleProperties(competitionId, emptyList()), mutableListOf()),
            dashboardState = CompetitionDashboardState(competitionId, emptySet()),
            status = CompetitionStatus.CREATED)


    fun withStatus(status: CompetitionStatus): CompetitionState {
        this.status = status
        return this
    }
}