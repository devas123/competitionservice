package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.payload.CategoryAddedPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class CategoryAddedPayloadValidator : AbstractEventPayloadValidator<CategoryAddedPayload>(CategoryAddedPayload::class.java) {
    override fun <F> validateEvent(validationRules: PayloadValidationRules<F>, payload: CategoryAddedPayload, event: EventDTO): Kind<F, CategoryAddedPayload> {
        return when {
            payload.categoryState == null -> validationRules.raiseError(PayloadValidationError.FieldMissing("categoryState", event.id).nel())
            payload.categoryState.id.isNullOrBlank() -> validationRules.raiseError(PayloadValidationError.FieldMissing("categoryState.id", event.id).nel())
            payload.categoryState.competitionId.isNullOrBlank() -> validationRules.raiseError(PayloadValidationError.FieldMissing("categoryState.competitionId", event.id).nel())
            payload.categoryState.category == null -> validationRules.raiseError(PayloadValidationError.FieldMissing("categoryState.category", event.id).nel())
            payload.categoryState.category.id == null -> validationRules.raiseError(PayloadValidationError.FieldMissing("categoryState.category.id", event.id).nel())
            event.categoryId.isNullOrBlank() -> validationRules.raiseError(PayloadValidationError.FieldMissing("event.categoryId", event.id).nel())
            else -> {
                validationRules.just(payload)
            }
        }
    }
}