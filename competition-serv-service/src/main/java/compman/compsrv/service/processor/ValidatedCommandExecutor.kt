package compman.compsrv.service.processor

import arrow.core.Either
import arrow.core.fix
import arrow.core.flatMap
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.CommandProcessingError
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.exceptions.CommandProcessingException
import compman.compsrv.service.processor.AbstractAggregateService.Companion.getPayloadAs
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator

abstract class ValidatedCommandExecutor<AT : AbstractAggregate>(val mapper: ObjectMapper, val validators: List<PayloadValidator>) {

    inline fun <reified T : Payload> executeValidatedMultiple(
        command: CommandDTO,
        crossinline logic: (payload: T, com: CommandDTO) -> Either<SagaExecutionError, List<EventDTO>>
    ): Either<SagaExecutionError, List<EventDTO>> {
        val payload = getPayloadAs<T>(command)!!
        return PayloadValidationRules
            .accumulateErrors { payload.validate(command, validators).fix() }
            .toEither()
            .mapLeft { SagaExecutionError.PayloadValidationFailed(it) }
            .flatMap { logic(payload, command) }
    }
    inline fun <reified T : Payload> executeValidated(
        command: CommandDTO,
        crossinline logic: (payload: T, com: CommandDTO) -> AggregateWithEvents<AT>
    ): Either<CommandProcessingError, AggregateWithEvents<AT>> {
        val payload = getPayloadAs<T>(command)!!
        return PayloadValidationRules
            .accumulateErrors { payload.validate(command, validators).fix() }
            .map { logic(payload, command) }
            .toEither()
            .mapLeft { CommandProcessingError.PayloadValidationFailed(it) }
    }

    protected fun Either<CommandProcessingError, AggregateWithEvents<AT>>.unwrap(command: CommandDTO) =
        this.fold({ throw CommandProcessingException(it.message, command) }, { it })
}
