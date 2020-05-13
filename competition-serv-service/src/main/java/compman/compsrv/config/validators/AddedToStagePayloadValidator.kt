package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.events.payload.FightsAddedToStagePayload
import compman.compsrv.model.events.payload.StageResultSetPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class AddedToStagePayloadValidator : AbstractCommandPayloadValidator<FightsAddedToStagePayload>(FightsAddedToStagePayload::class.java) {
    override fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: FightsAddedToStagePayload, command: CommandDTO): Kind<F, FightsAddedToStagePayload> {
        return when {
            payload.stageId.isNullOrBlank() -> validationRules.raiseError(PayloadValidationError.FieldMissing("stageId", command.id).nel())
            payload.fights.isNullOrEmpty() -> validationRules.raiseError(PayloadValidationError.FieldMissing("fights", command.id).nel())
            else -> {
                validationRules.just(payload)
            }
        }
    }
}