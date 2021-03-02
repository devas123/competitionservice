package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
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
            payload.fightId.isNullOrBlank() -> {
                validationRules.raiseError(PayloadValidationError.FieldMissing("payload.fightId", event.id).nel())
            }
            else -> {
                validationRules.just(payload)
            }
        }
    }
}