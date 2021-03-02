package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.payload.FightPropertiesUpdatedPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class FightPropertiesUpdatedPayloadValidator :
    AbstractEventPayloadValidator<FightPropertiesUpdatedPayload>(FightPropertiesUpdatedPayload::class.java) {
    override fun <F> validateEvent(
        validationRules: PayloadValidationRules<F>,
        payload: FightPropertiesUpdatedPayload,
        event: EventDTO
    ): Kind<F, FightPropertiesUpdatedPayload> {
        return when {
            payload.updates.isNullOrEmpty() -> {
                validationRules.raiseError(PayloadValidationError.FieldMissing("payload.updates", event.id).nel())
            }
            payload.updates.any { it.fightId.isNullOrEmpty() } -> {
                validationRules.raiseError(PayloadValidationError.FieldMissing("payload.updates.fightId", event.id).nel())
            }
            else -> {
                validationRules.just(payload)
            }
        }
    }
}