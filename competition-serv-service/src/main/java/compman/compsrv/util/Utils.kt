package compman.compsrv.util

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.competition.CompetitionProperties
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.jpa.schedule.Period
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.competition.WeightDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
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
    val endTime = period.fightsByMats?.map { it.currentTime }?.maxBy { it.toEpochMilli() }?.toEpochMilli()
            ?: startTime
    val durationMillis = endTime - startTime
    if (durationMillis > 0) {
        return BigDecimal.valueOf(durationMillis).divide(BigDecimal(1000 * 60 * 60), 2, RoundingMode.HALF_UP)
    }
    return null
}

fun ObjectMapper.createErrorEvent(command: CommandDTO, error: String?): EventDTO = EventDTO()
        .setId(IDGenerator.uid())
        .setCategoryId(command.categoryId)
        .setCorrelationId(command.correlationId)
        .setCompetitionId(command.competitionId)
        .setMatId(command.matId)
        .setType(EventType.ERROR_EVENT)
        .setPayload(writeValueAsString(ErrorEventPayload(error, command.id)))
fun ObjectMapper.createErrorEvent(event: EventDTO, error: String?): EventDTO = EventDTO()
        .setId(IDGenerator.uid())
        .setCategoryId(event.categoryId)
        .setCorrelationId(event.correlationId)
        .setCompetitionId(event.competitionId)
        .setMatId(event.matId)
        .setType(EventType.ERROR_EVENT)
        .setPayload(writeValueAsString(ErrorEventPayload(error, event.id)))

fun ObjectMapper.createEvent(command: CommandDTO, type: EventType, payload: Any?): EventDTO =
        EventDTO()
                .setCategoryId(command.categoryId)
                .setCorrelationId(command.correlationId)
                .setCompetitionId(command.competitionId)
                .setMatId(command.matId)
                .setType(type)
                .setPayload(writeValueAsString(payload))

fun <T> ObjectMapper.getPayloadAs(event: EventDTO , clazz: Class<T>): T? {
    return event.payload?.let {
        readValue(it, clazz)
    }
}

fun <T> ObjectMapper.getPayloadAs(payload: String? , clazz: Class<T>): T? {
    return payload?.let {
        readValue(it, clazz)
    }
}

fun <T> ObjectMapper.getPayloadAs(command: CommandDTO , clazz: Class<T>): T? {
    return command.payload?.let {
        convertValue(it, clazz)
    }
}



