package compman.compsrv.model.schedule

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.competition.FightDescription
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor

data class Schedule
@JsonCreator
@PersistenceConstructor
constructor(@Id @JsonProperty("competitionId") val competitionId: String,
            @JsonProperty("scheduleProperties") val scheduleProperties: ScheduleProperties?,
            @JsonProperty("periods") val periods: List<Period>?) {

    companion object {
        fun obsoleteFight(f: FightDescription, threeCompetitorCategory: Boolean): Boolean {
            if (threeCompetitorCategory) {
                return false
            }
            if ((f.parentId1 != null) || (f.parentId2 != null)) return false
            return !(f.competitors.size == 2 && f.competitors.all { compNotEmpty(it.competitor) })
        }

        fun compNotEmpty(comp: Competitor?): Boolean {
            if (comp == null) return false
            val firstName = comp.firstName
            val lastName = comp.lastName
            return firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty()
        }

    }

    override fun toString(): String {
        return "Schedule(competitionId='$competitionId', periods=$periods, scheduleProperties=$scheduleProperties)"
    }

    fun setScheduleProperties(scheduleProperties: ScheduleProperties?) = copy(scheduleProperties = scheduleProperties)
    fun setPeriods(periods: List<Period>?) = copy(periods = periods)
}