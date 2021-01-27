package compman.compsrv.service.processor

import arrow.core.Either
import arrow.core.fix
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.aggregate.Category
import compman.compsrv.aggregate.Competition
import compman.compsrv.aggregate.Competitor
import compman.compsrv.errors.EventApplicationError
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService.Companion.getPayloadAs
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator

abstract class ValidatedEventExecutor<AT : AbstractAggregate>(val mapper: ObjectMapper, val validators: List<PayloadValidator>) {

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

    protected fun Either<EventApplicationError, AT>.unwrap(event: EventDTO) =
        this.fold({ throw EventApplyingException(it.message, event) }, { it })

    protected fun AbstractAggregate.save(dbOperations: DBOperations) {
        when (this) {
            is Category -> dbOperations.putCategory(this)
            is Competition -> dbOperations.putCompetition(this)
            is Competitor -> dbOperations.putCompetitor(this)
        }
    }

}
