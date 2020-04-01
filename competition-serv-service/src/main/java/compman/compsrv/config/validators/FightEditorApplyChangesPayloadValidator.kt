package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.FightEditorApplyChangesPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class FightEditorApplyChangesPayloadValidator : AbstractCommandPayloadValidator<FightEditorApplyChangesPayload>(FightEditorApplyChangesPayload::class.java) {
    override fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: FightEditorApplyChangesPayload, command: CommandDTO): Kind<F, FightEditorApplyChangesPayload> {
        return when {
            payload.stageId.isNullOrBlank() -> {
                validationRules.raiseError(PayloadValidationError.FieldMissing("stageId", command.id).nel())
            }
            payload.fights.isNullOrEmpty() -> {
                validationRules.raiseError(PayloadValidationError.FieldMissing("fights", command.id).nel())
            }
            else -> {
                validationRules.just(payload)
            }
        }
    }
}