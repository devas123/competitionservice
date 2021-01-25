package compman.compsrv.service.processor.command

import arrow.core.Either
import arrow.core.extensions.either.monad.flatMap
import arrow.core.fix
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.CommandProcessingError
import compman.compsrv.errors.EventApplicationError
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.exceptions.CommandProcessingException
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.service.processor.command.AbstractAggregateService.Companion.getPayloadAs
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator

open class ValidatedExecutor<AT : AbstractAggregate>(val mapper: ObjectMapper, val validators: List<PayloadValidator>) {

    inline fun <reified T : Payload> executeValidatedMultiple(
        command: CommandDTO,
        crossinline logic: (payload: T, com: CommandDTO) -> Either<SagaExecutionError, List<AggregateWithEvents<AT>>>
    ): Either<SagaExecutionError, List<AggregateWithEvents<AT>>> {
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
    inline fun <reified T : Payload, V> executeValidated(
        event: EventDTO,
        crossinline logic: (payload: T, event: EventDTO) -> V
    ): Either<EventApplicationError, V> {
        val payload = getPayloadAs<T>(event)!!
        return PayloadValidationRules
            .accumulateErrors { payload.validate(event, validators).fix() }
            .map { logic(payload, event) }
            .toEither()
            .mapLeft { EventApplicationError.PayloadValidationFailed(it) }
    }

    protected fun Either<CommandProcessingError, AggregateWithEvents<AT>>.unwrap(command: CommandDTO) =
        this.fold({ throw CommandProcessingException(it.message, command) }, { it })

    protected fun Either<EventApplicationError, AT>.unwrap(event: EventDTO) =
        this.fold({ throw EventApplyingException(it.message, event) }, { it })

}
