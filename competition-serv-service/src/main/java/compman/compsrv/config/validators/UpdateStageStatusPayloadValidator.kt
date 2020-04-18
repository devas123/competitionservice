package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.UpdateStageStatusPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class UpdateStageStatusPayloadValidator : AbstractCommandPayloadValidator<UpdateStageStatusPayload>(UpdateStageStatusPayload::class.java) {
    override fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: UpdateStageStatusPayload, command: CommandDTO): Kind<F, UpdateStageStatusPayload> {
        return when {
            payload.stageId.isNullOrBlank() -> validationRules.raiseError(PayloadValidationError.FieldMissing("stageId", command.id).nel())
            payload.status == null -> validationRules.raiseError(PayloadValidationError.FieldMissing("status", command.id).nel())
            else -> {
                validationRules.just(payload)
            }
        }
    }
}