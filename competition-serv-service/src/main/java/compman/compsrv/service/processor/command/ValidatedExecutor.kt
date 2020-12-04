package compman.compsrv.service.processor.command

import arrow.core.Either
import arrow.core.fix
import arrow.core.flatMap
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.CommandProcessingError
import compman.compsrv.errors.EventApplicationError
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.createEvent
import compman.compsrv.util.getPayloadAs

open class ValidatedExecutor<AT : AbstractAggregate>(val mapper: ObjectMapper, val validators: List<PayloadValidator>) {
    protected fun createEvent(command: CommandDTO, eventType: EventType, payload: Any?) = mapper.createEvent(command, eventType, payload)
    inline fun <reified T : Payload> executeValidatedMultiple(
        command: CommandDTO, payloadClass: Class<T>,
        crossinline logic: (payload: T, com: CommandDTO) -> Either<SagaExecutionError, List<AggregateWithEvents<AT>>>
    ): Either<SagaExecutionError, List<AggregateWithEvents<AT>>> {
        val payload = mapper.getPayloadAs(command, payloadClass)!!
        return PayloadValidationRules
            .accumulateErrors { payload.validate(command, validators).fix() }
            .toEither()
            .mapLeft { SagaExecutionError.PayloadValidationFailed(it) }
            .flatMap { logic(payload, command) }
    }
    inline fun <reified T : Payload> executeValidated(command: CommandDTO, payloadClass: Class<T>,
                                                      crossinline logic: (payload: T, com: CommandDTO) -> AggregateWithEvents<AT>): Either<CommandProcessingError, AggregateWithEvents<AT>> {
        val payload = mapper.getPayloadAs(command, payloadClass)!!
        return PayloadValidationRules
            .accumulateErrors { payload.validate(command, validators).fix() }
            .map { logic(payload, command) }
            .toEither()
            .mapLeft { CommandProcessingError.PayloadValidationFailed(it) }
    }
    inline fun <reified T : Payload> executeValidated(event: EventDTO, payloadClass: Class<T>,
                                                      crossinline logic: (payload: T, event: EventDTO) -> Unit) {
        val payload = mapper.getPayloadAs(event, payloadClass)!!
        PayloadValidationRules
            .accumulateErrors { payload.validate(event, validators).fix() }
            .map { logic(payload, event) }
            .toEither()
            .mapLeft { EventApplicationError.PayloadValidationFailed(it) }
    }
}
