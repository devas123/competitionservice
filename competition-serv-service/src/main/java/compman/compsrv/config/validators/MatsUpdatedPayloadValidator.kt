package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.payload.MatsUpdatedPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class MatsUpdatedPayloadValidator :
    AbstractEventPayloadValidator<MatsUpdatedPayload>(
        MatsUpdatedPayload::class.java) {
    override fun <F> validateEvent(
        validationRules: PayloadValidationRules<F>,
        payload: MatsUpdatedPayload,
        event: EventDTO
    ): Kind<F, MatsUpdatedPayload> {
        return when {
            payload.mats.isNullOrEmpty() -> {
                validationRules.raiseError(PayloadValidationError.FieldMissing("payload.mats", event.id).nel())
            }
            payload.mats.any { it.id.isNullOrBlank() } -> {
                validationRules.raiseError(PayloadValidationError.FieldMissing("payload.mat.id", event.id).nel())
            }
            else -> {
                validationRules.just(payload)
            }
        }
    }
}