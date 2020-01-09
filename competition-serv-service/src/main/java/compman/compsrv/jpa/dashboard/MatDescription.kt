package compman.compsrv.jpa.dashboard

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.FightDescription
import javax.persistence.*

@Entity
class MatDescription(
        id: String,
        var name: String?,
        @ManyToOne
        @JoinColumn(name = "dashboard_period_id", nullable = false)
        var dashboardPeriod: DashboardPeriod?) : AbstractJpaPersistable<String>(id)
