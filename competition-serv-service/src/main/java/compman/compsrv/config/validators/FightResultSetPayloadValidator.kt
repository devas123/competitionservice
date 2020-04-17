package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.events.payload.StageResultSetPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class FightResultSetPayloadValidator : AbstractCommandPayloadValidator<SetFightResultPayload>(SetFightResultPayload::class.java) {
    override fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: SetFightResultPayload, command: CommandDTO): Kind<F, SetFightResultPayload> {
        return when {
            payload.fightId.isNullOrBlank() -> validationRules.raiseError(PayloadValidationError.FieldMissing("fightId", command.id).nel())
            payload.fightResult == null -> validationRules.raiseError(PayloadValidationError.FieldMissing("fightResult", command.id).nel())
            payload.scores.isNullOrEmpty() -> validationRules.raiseError(PayloadValidationError.FieldMissing("scores", command.id).nel())
            else -> {
                validationRules.just(payload)
            }
        }
    }
}