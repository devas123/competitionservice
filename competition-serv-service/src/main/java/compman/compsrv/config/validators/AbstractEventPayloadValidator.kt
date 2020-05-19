package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.Ior
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractEventPayloadValidator<P: Payload>(private val payloadClass: Class<P>) : PayloadValidator {
    protected val log: Logger = LoggerFactory.getLogger(AbstractEventPayloadValidator::class.java)
    override fun canValidate(payload: Payload): Boolean {
        return payloadClass.isInstance(payload)
    }

    abstract fun <F> validateEvent(validationRules: PayloadValidationRules<F>, payload: P, event: EventDTO): Kind<F, P>
    @SuppressWarnings("all", "unchecked", "rawtypes")
    override fun <F, T : Payload> validate(validationRules: PayloadValidationRules<F>, payload: T, comEv: Ior<CommandDTO, EventDTO>): Kind<F, T> {
        log.info("Validating payload: $payload")
        return kotlin.runCatching {
            if (comEv.isRight) {
                comEv.fold({ com ->
                    validationRules.raiseError<T>(PayloadValidationError.GenericError("This is an event payload, but is a part of a command.", com.id).nel())
                }, { ev ->
                    validateEvent(validationRules, payloadClass.cast(payload), ev) as Kind<F, T>
                }, { _, e ->
                    validationRules.raiseError(PayloadValidationError.GenericError("This is an event payload, but is a part of a command.", e.id).nel())
                })
            } else {
                validationRules.just(payload)
            }
        }.getOrElse { t -> validationRules.raiseError(PayloadValidationError.UnexpectedException(t, comEv.fold({it.id}, {it.id}, {c, e -> c.id + e.id})).nel()) }
    }

}