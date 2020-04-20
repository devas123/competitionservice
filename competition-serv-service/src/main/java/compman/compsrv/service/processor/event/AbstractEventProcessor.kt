package compman.compsrv.service.processor.event

import arrow.core.fix
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.getPayloadAs
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractEventProcessor(val mapper: ObjectMapper, val validators: List<PayloadValidator>) : IEventProcessor {
    val log: Logger = LoggerFactory.getLogger(AbstractEventProcessor::class.java)

    inline fun <reified T : Payload> executeValidated(event: EventDTO, payloadClass: Class<T>,
                                                      crossinline logic: (payload: T, event: EventDTO) -> Unit): List<EventDTO> {
        val payload = mapper.getPayloadAs(event, payloadClass)!!
        return kotlin.runCatching {
            PayloadValidationRules
                    .accumulateErrors { payload.validate(event, validators).fix() }
                    .map { logic(payload, event) }
                    .fold({ it.map { p -> mapper.createErrorEvent(event, p) }.all }, { emptyList() })
        }
                .getOrElse {
                    log.error("Error during event execution: $event", it)
                    listOf(mapper.createErrorEvent(event, "Error during event processing: ${it.message}"))
                }
    }
    inline fun <reified T : Payload> executeWithEffects(event: EventDTO, payloadClass: Class<T>,
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
}