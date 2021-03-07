package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.Ior
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class AbstractCommandPayloadValidator<P: Payload>(private val payloadClass: Class<P>) : PayloadValidator {
    protected val log: Logger = LoggerFactory.getLogger(AbstractCommandPayloadValidator::class.java)
    override fun canValidate(payload: Payload): Boolean {
        return payloadClass.isInstance(payload)
    }

    abstract fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: P, command: CommandDTO): Kind<F, P>
    @Suppress("all", "UNCHECKED_CAST", "rawtypes")
    override fun <F, T : Payload> validate(validationRules: PayloadValidationRules<F>, payload: T, comEv: Ior<CommandDTO, EventDTO>): Kind<F, T> {
        log.debug("Validating payload: $payload")
        return kotlin.runCatching {
            if (comEv.isLeft) {
                comEv.fold({ com ->
                    if (com.competitionId.isNullOrBlank()) {
                        validationRules.raiseError(PayloadValidationError.FieldMissing("command.competitionId", com.id).nel())
                    } else {
                        validateCommand(validationRules, payloadClass.cast(payload), com) as Kind<F, T>
                    }
                }, { validationRules.raiseError(PayloadValidationError.GenericError("This is a command payload, but is a part of event.", it.id).nel()) }, { _, e ->
                    validationRules.raiseError(PayloadValidationError.GenericError("This is a command payload, but is a part of event.", e.id).nel())
                })
            } else {
                validationRules.just(payload)
            }
        }.getOrElse { t -> validationRules.raiseError(PayloadValidationError.UnexpectedException(t, comEv.fold({it.id}, {it.id}, {c, e -> c.id + e.id})).nel()) }
    }

}