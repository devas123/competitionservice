package compman.compsrv.service.processor

import arrow.core.Either
import arrow.core.fix
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.CommandProcessingError
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.exceptions.CommandProcessingException
import compman.compsrv.service.processor.AbstractAggregateService.Companion.getPayloadAs
import compman.compsrv.service.processor.saga.SagaStep
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator

abstract class ValidatedCommandExecutor<AT : AbstractAggregate>(val mapper: ObjectMapper, val validators: List<PayloadValidator>) {

    protected inline fun <reified T : Payload> createSaga(
        command: CommandDTO,
        crossinline logic: (payload: T, com: CommandDTO) -> SagaStep<List<EventDTO>>
    ): Either<SagaExecutionError, SagaStep<List<EventDTO>>> {
        val payload = getPayloadAs<T>(command)!!
        return PayloadValidationRules
            .accumulateErrors { payload.validate(command, validators).fix() }
            .toEither()
            .mapLeft { SagaExecutionError.PayloadValidationFailed(it) }
            .map { logic(payload, command) }
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
