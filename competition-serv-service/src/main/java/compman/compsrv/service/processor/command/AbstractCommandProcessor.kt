package compman.compsrv.service.processor.command

import arrow.core.fix
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.getPayloadAs

abstract class AbstractCommandProcessor(val mapper: ObjectMapper, val validators: List<PayloadValidator>) : ICommandProcessor {
    inline fun <reified T : Payload> executeValidated(command: CommandDTO, payloadClass: Class<T>,
                                                      crossinline logic: (payload: T, com: CommandDTO) -> List<EventDTO>): List<EventDTO> {
        val payload = mapper.getPayloadAs(command, payloadClass)!!
        return kotlin.runCatching {
                    PayloadValidationRules
                            .accumulateErrors { payload.validate(command, validators).fix() }
                            .map { logic(payload, command) }
                            .fold({ it.map { p -> mapper.createErrorEvent(command, p) }.all }, { it })
                }
                .getOrElse { listOf(mapper.createErrorEvent(command, "Error during command execuion: ${it.message}")) }
    }
}