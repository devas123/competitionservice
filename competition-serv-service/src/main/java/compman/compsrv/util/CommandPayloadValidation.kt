package compman.compsrv.util

import arrow.Kind
import arrow.core.*
import arrow.core.extensions.either.applicativeError.applicativeError
import arrow.core.extensions.nonemptylist.semigroup.semigroup
import arrow.core.extensions.validated.applicativeError.applicativeError
import arrow.typeclasses.ApplicativeError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.DashboardFightOrderChangePayload
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.commands.payload.PropagateCompetitorsPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.payload.ErrorEventPayload

sealed class PayloadValidationError(msg: String, failedOn: String) : ErrorEventPayload(msg, failedOn) {
    data class FieldMissing(val fieldName: String, val on: String) : PayloadValidationError("Did not contain $fieldName", on)
    data class PayloadNull(val on: String) : PayloadValidationError("Payload is null", on)
    data class GenericError(val message: String, val on: String) : PayloadValidationError(message, on)
    data class UnexpectedException(val throwable: Throwable, val on: String) : PayloadValidationError(throwable.localizedMessage, on)
    data class NotValid(val value: Any, val reasons: Nel<PayloadValidationError>, val on: String) : PayloadValidationError("$value is not valid: ${reasons.toList().joinToString(", ")}", on)
}

sealed class PayloadValidationRules<F>(private val A: ApplicativeError<F, Nel<PayloadValidationError>>) : ApplicativeError<F, Nel<PayloadValidationError>> by A {

    private fun DashboardFightOrderChangePayload.validate(com: CommandDTO): Kind<F, DashboardFightOrderChangePayload> {
        return if (!fightId.isNullOrBlank() && !currentMatId.isNullOrBlank() && !newMatId.isNullOrBlank() && newOrderOnMat != null && currentOrderOnMat != null) {
            just(this)
        } else {
            raiseError(PayloadValidationError.GenericError("Not enough information in the payload.", com.id).nel())
        }
    }

    private fun PropagateCompetitorsPayload.validate(com: CommandDTO): Kind<F, PropagateCompetitorsPayload> {
        return listOf(propagateToStageId, previousStageId).foldIndexed(just(this)) { ind, kind, s ->
            if (s.isNullOrBlank()) {
                val name = if (ind == 0) {
                    "propagateToStageId"
                } else {
                    "previousStageId"
                }
                raiseError(PayloadValidationError.FieldMissing(name, com.id).nel())
            } else {
                kind
            }
        }
    }

    fun Payload.validate(com: CommandDTO, validators: Iterable<PayloadValidator>): Kind<F, Payload> =
            when (this) {
                is DashboardFightOrderChangePayload ->
                    this.validate(com)
                is PropagateCompetitorsPayload ->
                    this.validate(com)
                else ->
                    validators.filter { it.canValidate(this) }.fold(just(this)) { k, p ->
                        map(k, p.validate(this@PayloadValidationRules, this, Ior.Left(com))) { this }.handleErrorWith { raiseError(it) }
                    }
            }

    fun Payload.validate(event: EventDTO, validators: Iterable<PayloadValidator>): Kind<F, Payload> =
            validators.filter { it.canValidate(this) }.fold(just(this)) { k, p ->
                map(k, p.validate(this@PayloadValidationRules, this, Ior.Right(event))) { this }.handleErrorWith { raiseError(it) }
            }


    object ErrorAccumulationStrategy :
            PayloadValidationRules<ValidatedPartialOf<Nel<PayloadValidationError>>>(Validated.applicativeError(NonEmptyList.semigroup()))

    object FailFastStrategy :
            PayloadValidationRules<EitherPartialOf<Nel<PayloadValidationError>>>(Either.applicativeError())

    companion object {
        infix fun <A> failFast(f: FailFastStrategy.() -> A): A = f(FailFastStrategy)
        infix fun <A> accumulateErrors(f: ErrorAccumulationStrategy.() -> A): A = f(ErrorAccumulationStrategy)
    }
}

interface PayloadValidator {
    fun canValidate(payload: Payload): Boolean
    fun <F, T : Payload> validate(validationRules: PayloadValidationRules<F>, payload: T, comEv: Ior<CommandDTO, EventDTO>): Kind<F, T>
}
