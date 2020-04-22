package compman.compsrv.service.processor.event

import arrow.core.fix
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitionPropertiesUpdatedPayload
import compman.compsrv.model.events.payload.ScheduleGeneratedPayload
import compman.compsrv.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EventEffects(val mapper: ObjectMapper,
                   val validators: List<PayloadValidator>): IEffects {
    companion object {
        val log: Logger = LoggerFactory.getLogger(EventEffects::class.java)
    }
    private inline fun <reified T : Payload> executeWithEffects(event: EventDTO, payloadClass: Class<T>,
                                                                crossinline logic: (payload: T, event: EventDTO) -> List<EventDTO>): List<EventDTO> {
        val payload = mapper.getPayloadAs(event, payloadClass)!!
        return kotlin.runCatching {
            PayloadValidationRules
                    .accumulateErrors { payload.validate(event, validators).fix() }
                    .map { logic(payload, event) }
                    .fold({ it.map { p -> mapper.createErrorEvent(event, p) }.all }, { it })
        }
                .getOrElse {
                    log.error("Error during event execution: $event", it)
                    listOf(mapper.createErrorEvent(event, "Error during event processing: ${it.message}"))
                }
    }


    override fun effects(event: EventDTO): List<EventDTO> {
        return when(event.type) {
            EventType.SCHEDULE_GENERATED -> executeWithEffects(event, ScheduleGeneratedPayload::class.java) { _, _ ->
                listOf(mapper.createEffect(event, EventType.COMPETITION_PROPERTIES_UPDATED,
                        CompetitionPropertiesUpdatedPayload().setProperties(mapOf("schedulePublished" to true))))
            }
            EventType.SCHEDULE_DROPPED -> listOf(mapper.createEffect(event, EventType.COMPETITION_PROPERTIES_UPDATED,
                    CompetitionPropertiesUpdatedPayload().setProperties(mapOf("schedulePublished" to false))))
            else -> emptyList()
        }
    }
}