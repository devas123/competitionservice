package compman.compsrv.service

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.schedule.*
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

internal data class InternalSchedule
@JsonCreator
@PersistenceConstructor
constructor(@Id @JsonProperty("competitionId") val competitionId: String,
            @JsonProperty("scheduleProperties") val scheduleProperties: ScheduleProperties?,
            @JsonProperty("periods") val periods: List<InternalPeriod>?) {


    private fun getDuration(period: InternalPeriod): BigDecimal? {
        val startTime = Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(period.startTime))).time
        val endTime = period.fightsByMats?.map { it.currentTime }?.sortedBy { it.time }?.lastOrNull()?.time ?: startTime
        val durationMillis = endTime - startTime
        if (durationMillis > 0) {
            return BigDecimal.valueOf(durationMillis).divide(BigDecimal(1000 * 60 * 60), 2, RoundingMode.HALF_UP)
        }
        return null
    }

    fun toSchedule(): Schedule {
        return Schedule(competitionId, scheduleProperties, periods?.map { intPer ->
            Period(intPer.id, intPer.name, intPer.schedule, intPer.startTime, getDuration(intPer), intPer.numberOfMats, intPer.fightsByMats?.map { internalMatScheduleContainer ->
                MatScheduleContainer(internalMatScheduleContainer.currentTime, internalMatScheduleContainer.matId, internalMatScheduleContainer.fights.map {
                    FightScheduleInfo(it.fight.fightId, it.fightNumber, it.startTime)
                })
            })
        })
    }
}