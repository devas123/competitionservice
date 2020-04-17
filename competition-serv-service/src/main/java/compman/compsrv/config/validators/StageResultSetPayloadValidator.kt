package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.payload.StageResultSetPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class StageResultSetPayloadValidator : AbstractCommandPayloadValidator<StageResultSetPayload>(StageResultSetPayload::class.java) {
    override fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: StageResultSetPayload, command: CommandDTO): Kind<F, StageResultSetPayload> {
        return when {
            payload.stageId.isNullOrBlank() -> validationRules.raiseError(PayloadValidationError.FieldMissing("stageId", command.id).nel())
            payload.results.isNullOrEmpty() -> validationRules.raiseError(PayloadValidationError.FieldMissing("results", command.id).nel())
            else -> {
                validationRules.just(payload)
            }
        }
    }
}