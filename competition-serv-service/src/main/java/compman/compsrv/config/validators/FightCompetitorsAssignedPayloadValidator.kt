package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.payload.FightCompetitorsAssignedPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class FightCompetitorsAssignedPayloadValidator : AbstractCommandPayloadValidator<FightCompetitorsAssignedPayload>(FightCompetitorsAssignedPayload::class.java) {
    override fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: FightCompetitorsAssignedPayload, command: CommandDTO): Kind<F, FightCompetitorsAssignedPayload> {
        return when {
            payload.assignments.isNullOrEmpty() -> validationRules.raiseError(PayloadValidationError.FieldMissing("assignments", command.id).nel())
            else -> {
                validationRules.just(payload)
            }
        }
    }
}