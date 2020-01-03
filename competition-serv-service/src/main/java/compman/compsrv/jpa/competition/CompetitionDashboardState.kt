package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.schedule.DashboardPeriod
import compman.compsrv.model.dto.competition.CompetitionDashboardStateDTO
import org.hibernate.annotations.Cascade
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.OneToMany

@Entity(name = "dashboard_state")
class CompetitionDashboardState(
        id: String,
        @OneToMany(orphanRemoval = true)
        @Cascade(org.hibernate.annotations.CascadeType.ALL)
        @JoinColumn(name = "DASHBOARD_ID")
        var periods: Set<DashboardPeriod>) : AbstractJpaPersistable<String>(id) {
    override fun toString(): String {
        return "CompetitionDashboardState(competitionId='$id', periods=$periods)"
    }
}