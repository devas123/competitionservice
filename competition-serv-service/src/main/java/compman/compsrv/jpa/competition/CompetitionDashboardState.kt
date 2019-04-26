package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.schedule.DashboardPeriod
import compman.compsrv.model.dto.competition.CompetitionDashboardStateDTO
import javax.persistence.CascadeType
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.OneToMany

@Entity(name = "dashboard_state")
class CompetitionDashboardState(
        id: String,
        @OneToMany(orphanRemoval = true, cascade = [CascadeType.ALL])
        @JoinColumn(name = "DASHBOARD_ID")
        var periods: Set<DashboardPeriod>) : AbstractJpaPersistable<String>(id) {

    companion object {
        fun fromDTO(dto: CompetitionDashboardStateDTO) = CompetitionDashboardState(dto.competitionId, dto.periods.map { DashboardPeriod.fromDTO(it) }.toSet())
    }

    override fun toString(): String {
        return "CompetitionDashboardState(competitionId='$id', periods=$periods)"
    }

    fun toDTO(): CompetitionDashboardStateDTO? {
        return CompetitionDashboardStateDTO()
                .setCompetitionId(id)
                .setPeriods(periods.map { it.toDTO() }.toTypedArray())
    }
}