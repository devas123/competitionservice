package compman.compsrv.util

import compman.compsrv.jpa.competition.CompetitionProperties
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.jpa.competition.Weight
import compman.compsrv.jpa.schedule.Period
import compman.compsrv.model.dto.competition.WeightDTO
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

private fun parseDate(date: Any?, default: Instant?) = if (date != null && !date.toString().isBlank()) {
    Instant.ofEpochMilli(date.toString().toLong())
} else {
    default
}

fun getId(name: String) = IDGenerator.hashString(name)

fun compareWeightNames(w1: String, w2: String) = Comparator.comparingInt { w: String -> WeightDTO.WEIGHT_NAMES.indexOfFirst { it.toLowerCase() == w.trim().toLowerCase() } }.compare(w1, w2)


fun CompetitionProperties?.applyProperties(props: Map<String, Any?>?) = this?.also {
    if (props != null) {
        bracketsPublished = props["bracketsPublished"] as? Boolean ?: bracketsPublished
        startDate = parseDate(props["startDate"], startDate)
        endDate = parseDate(props["endDate"], endDate)
        emailNotificationsEnabled = props["emailNotificationsEnabled"] as? Boolean ?: emailNotificationsEnabled
        competitionName = props["competitionName"] as String? ?: competitionName
        emailTemplate = props["emailTemplate"] as? String ?: emailTemplate
        schedulePublished = props["schedulePublished"] as? Boolean ?: schedulePublished
        timeZone = props["timeZone"]?.toString() ?: timeZone
    }
}

fun compNotEmpty(comp: Competitor?): Boolean {
    if (comp == null) return false
    val firstName = comp.firstName
    val lastName = comp.lastName
    return firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty()
}

fun getPeriodDuration(period: Period): BigDecimal? {
    val startTime = period.startTime.toEpochMilli()
    val endTime = period.fightsByMats?.map { it.currentTime }?.sortedBy { it.toEpochMilli() }?.lastOrNull()?.toEpochMilli()
            ?: startTime
    val durationMillis = endTime - startTime
    if (durationMillis > 0) {
        return BigDecimal.valueOf(durationMillis).divide(BigDecimal(1000 * 60 * 60), 2, RoundingMode.HALF_UP)
    }
    return null
}
