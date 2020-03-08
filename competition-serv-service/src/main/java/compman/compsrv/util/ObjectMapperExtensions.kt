package compman.compsrv.util

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload


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

fun <T> ObjectMapper.getPayloadFromString(payload: String?, clazz: Class<T>): T? {
    return payload?.let {
        readValue(it, clazz)
    }
}

fun <T> ObjectMapper.getPayloadAs(command: CommandDTO , clazz: Class<T>): T? {
    return command.payload?.let {
        convertValue(it, clazz)
    }
}