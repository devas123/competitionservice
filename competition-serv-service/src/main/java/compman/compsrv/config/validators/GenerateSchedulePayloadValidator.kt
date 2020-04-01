package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.*
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.FightEditorApplyChangesPayload
import compman.compsrv.model.commands.payload.GenerateBracketsPayload
import compman.compsrv.model.commands.payload.GenerateSchedulePayload
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.dto.brackets.StageType
import compman.compsrv.model.dto.schedule.ScheduleRequirementType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GenerateSchedulePayloadValidator : AbstractCommandPayloadValidator<GenerateSchedulePayload>(GenerateSchedulePayload::class.java) {
    override fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: GenerateSchedulePayload, command: CommandDTO): Kind<F, GenerateSchedulePayload> {
        return when {
            payload.periods.isNullOrEmpty() -> validationRules.raiseError(PayloadValidationError.FieldMissing("periods", command.id).nel())
            !payload.periods.all { p -> !p.scheduleRequirements.isNullOrEmpty() } -> validationRules.raiseError(PayloadValidationError.FieldMissing("periods.scheduleRequirements", command.id).nel())
            !payload.periods.all { p -> p.scheduleRequirements.all{ sr -> sr.entryType != null } } -> validationRules.raiseError(PayloadValidationError.FieldMissing("periods.scheduleRequirements.entryType", command.id).nel())
            !payload.periods.all { p -> p.scheduleRequirements.filter{ sr -> sr.entryType != ScheduleRequirementType.FIXED_PAUSE }.all { sr -> sr.entryOrder != null } } -> validationRules.raiseError(PayloadValidationError.FieldMissing("periods.scheduleRequirements.entryOrder", command.id).nel())
            else -> {
                validationRules.just(payload)
            }
        }
    }
}