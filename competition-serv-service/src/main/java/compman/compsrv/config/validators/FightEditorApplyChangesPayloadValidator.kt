package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.*
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.FightEditorApplyChangesPayload
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FightEditorApplyChangesPayloadValidator : PayloadValidator {
    private val log: Logger = LoggerFactory.getLogger(FightEditorApplyChangesPayloadValidator::class.java)
    override fun canValidate(payload: Payload): Boolean {
        return payload is FightEditorApplyChangesPayload
    }

    override fun <F, T : Payload> validate(validationRules: PayloadValidationRules<F>, payload: T, comEv: Ior<CommandDTO, EventDTO>): Kind<F, T> {
        log.info("Validating payload: $payload")
        if (payload is FightEditorApplyChangesPayload && comEv.isLeft) {

            return comEv.fold({ com ->
                when {
                    payload.stageId.isNullOrBlank() -> {
                        validationRules.raiseError(PayloadValidationError.FieldMissing("stageId", com.id).nel())
                    }
                    payload.fights.isNullOrEmpty() -> {
                        validationRules.raiseError(PayloadValidationError.FieldMissing("fights", com.id).nel())
                    }
                    else -> {
                        validationRules.just(payload)
                    }
                }
            }, { validationRules.raiseError(PayloadValidationError.GenericError("This is a command payload, but is a part of event.", it.id).nel()) }, { _, e ->
                validationRules.raiseError(PayloadValidationError.GenericError("This is a command payload, but is a part of event.", e.id).nel())
            })
        }
        return validationRules.just(payload)
    }
}