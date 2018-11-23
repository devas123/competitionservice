package compman.compsrv.model.schedule

import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.competition.FightDescription
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*
import javax.persistence.*

@Entity
data class Schedule(@Id val id: String,
                    @Embedded
                    val scheduleProperties: ScheduleProperties?,
                    @OneToMany(orphanRemoval = true)
                    @JoinColumn(name = "SCHED_ID")
                    val periods: List<Period>?) {

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

    private fun getDuration(period: Period): BigDecimal? {
        val startTime = Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(period.startTime))).time
        val endTime = period.fightsByMats?.map { it.currentTime }?.sortedBy { it.time }?.lastOrNull()?.time ?: startTime
        val durationMillis = endTime - startTime
        if (durationMillis > 0) {
            return BigDecimal.valueOf(durationMillis).divide(BigDecimal(1000 * 60 * 60), 2, RoundingMode.HALF_UP)
        }
        return null
    }
}