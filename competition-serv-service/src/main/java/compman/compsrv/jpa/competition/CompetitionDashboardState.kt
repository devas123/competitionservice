package compman.compsrv.jpa.competition

import compman.compsrv.jpa.schedule.DashboardPeriod
import compman.compsrv.model.dto.competition.CompetitionDashboardStateDTO
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.OneToMany

@Entity
data class CompetitionDashboardState(
        @Id
        val competitionId: String,
        @OneToMany(orphanRemoval = true)
        @JoinColumn(name = "DASHBOARD_ID")
        val periods: Set<DashboardPeriod>) {

    companion object {
        fun fromDTO(dto: CompetitionDashboardStateDTO) = CompetitionDashboardState(dto.competitionId, dto.periods.map { DashboardPeriod.fromDTO(it) }.toSet())
    }

    override fun toString(): String {
        return "CompetitionDashboardState(competitionId='$competitionId', periods=$periods)"
    }

    fun upsertPeriod(period: DashboardPeriod) = if (periods.contains(period)) {
        copy(periods = (periods.filter { it.id != period.id } + period).toSet())
    } else {
        copy(periods = periods + period)
    }

    fun deletePeriod(periodId: String) = copy(periods = periods.filter { it.id != periodId }.toSet())
}