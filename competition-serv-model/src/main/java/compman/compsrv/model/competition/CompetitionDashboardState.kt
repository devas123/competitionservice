package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.schedule.DashboardPeriod
import org.springframework.data.annotation.PersistenceConstructor

data class CompetitionDashboardState @PersistenceConstructor @JsonCreator
constructor(
        @JsonProperty("eventOffset") val eventOffset: Long,
        @JsonProperty("eventPartition") val eventPartition: Int,
        @JsonProperty("competitionId") val competitionId: String,
        @JsonProperty("periods") val periods: Set<DashboardPeriod>,
        @JsonProperty("properties") val properties: CompetitionProperties?) {

    override fun toString(): String {
        return "CompetitionDashboardState(competitionId='$competitionId', periods=$periods, properties=$properties)"
    }

    fun setEventOffset(eventOffset: Long) = copy(eventOffset = eventOffset)
    fun setEventPartition(eventPartition: Int) = copy(eventPartition = eventPartition)

    fun upsertPeriod(period: DashboardPeriod) = if (periods.contains(period)) {
        copy(periods = (periods.filter { it.id != period.id } + period).toSet())
    } else {
        copy(periods = periods + period)
    }

    fun deletePeriod(periodId: String) = copy(periods = periods.filter { it.id != periodId }.toSet())

}