package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.jpa.schedule.ScheduleProperties
import compman.compsrv.model.dto.competition.CompetitionStateDTO
import compman.compsrv.model.dto.competition.CompetitionStatus
import org.hibernate.annotations.Cascade
import java.io.Serializable
import javax.persistence.*

@Entity(name = "competition_state")
class CompetitionState(id: String,
                       @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY, mappedBy = "competition")
                       @Cascade(org.hibernate.annotations.CascadeType.ALL)
                       var categories: List<CategoryState>,
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
}