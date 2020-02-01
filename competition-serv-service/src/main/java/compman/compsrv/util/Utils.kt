package compman.compsrv.util

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
import java.time.Instant

private fun parseDate(date: Any?, default: Instant?) = if (date != null && !date.toString().isBlank()) {
    Instant.ofEpochMilli(date.toString().toLong())
} else {
    default
}

fun getId(name: String) = IDGenerator.hashString(name)

fun CompetitionPropertiesDTO.applyProperties(props: Map<String, Any?>?) = CompetitionPropertiesDTO().also {
    if (props != null) {
        bracketsPublished = props["bracketsPublished"] as? Boolean ?: this.bracketsPublished
        startDate = parseDate(props["startDate"], this.startDate)
        endDate = parseDate(props["endDate"], this.endDate)
        emailNotificationsEnabled = props["emailNotificationsEnabled"] as? Boolean ?: this.emailNotificationsEnabled
        competitionName = props["competitionName"] as String? ?: this.competitionName
        emailTemplate = props["emailTemplate"] as? String ?: this.emailTemplate
        schedulePublished = props["schedulePublished"] as? Boolean ?: this.schedulePublished
        timeZone = props["timeZone"]?.toString() ?: this.timeZone
    }
}

fun compNotEmpty(comp: CompetitorDTO?): Boolean {
    if (comp == null) return false
    val firstName = comp.firstName
    val lastName = comp.lastName
    return firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty()
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



