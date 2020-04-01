package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.*
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.FightEditorApplyChangesPayload
import compman.compsrv.model.commands.payload.GenerateBracketsPayload
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.dto.brackets.StageType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import compman.compsrv.util.PayloadValidator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class GenerateBracketsPayloadValidator : AbstractCommandPayloadValidator<GenerateBracketsPayload>(GenerateBracketsPayload::class.java) {
    override fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: GenerateBracketsPayload, command: CommandDTO): Kind<F, GenerateBracketsPayload> {
        return when {
            payload.stageDescriptors.isNullOrEmpty() -> validationRules.raiseError(PayloadValidationError.FieldMissing("stageDescriptors", command.id).nel())
            !payload.stageDescriptors.all { sd -> !sd.id.isNullOrBlank() } -> validationRules.raiseError(PayloadValidationError.GenericError("Not all stages have id.", command.id).nel())
            !payload.stageDescriptors.all { sd -> sd.stageOrder != null } -> validationRules.raiseError(PayloadValidationError.GenericError("Not all stages have order.", command.id).nel())
            !payload.stageDescriptors.all { sd -> sd.stageType != null } -> validationRules.raiseError(PayloadValidationError.GenericError("Not all stages have stage type.", command.id).nel())
            !payload.stageDescriptors.all { sd -> sd.bracketType != null } -> validationRules.raiseError(PayloadValidationError.GenericError("Not all stages have brackets type.", command.id).nel())
            !payload.stageDescriptors.all { sd -> sd.stageResultDescriptor != null } ->
                validationRules.raiseError(PayloadValidationError.GenericError("Not all stages have result descriptor.", command.id).nel())
            !payload.stageDescriptors.none { sd -> sd.stageResultDescriptor.fightResultOptions.isNullOrEmpty() } ->
                validationRules.raiseError(PayloadValidationError.GenericError("Not all stages have fight result options.", command.id).nel())
            !payload.stageDescriptors.none { sd -> sd.stageResultDescriptor.fightResultOptions.any { fr -> fr.shortName.isNullOrBlank() } } ->
                validationRules.raiseError(PayloadValidationError.GenericError("Some stages have fight result options without names.", command.id).nel())
            !payload.stageDescriptors.filter { f -> f.stageType == StageType.PRELIMINARY }.all { sd -> sd.stageResultDescriptor?.outputSize != null } ->
                validationRules.raiseError(PayloadValidationError.GenericError("Not all preliminary stages have output size.", command.id).nel())
            !payload.stageDescriptors.filter { f -> f.stageOrder > 0 }.all { sd -> sd.inputDescriptor?.numberOfCompetitors != null
                    && sd.inputDescriptor.numberOfCompetitors > 0 } ->
                validationRules.raiseError(PayloadValidationError.GenericError("Not all stages with order > 0 have input size > 0.", command.id).nel())
            !validateSelectorsReferences(payload) -> validationRules.raiseError(PayloadValidationError.GenericError("Stage input selectors have invalid references.", command.id).nel())
            else -> {

                validationRules.just(payload)
            }
        }
    }

    private fun validateSelectorsReferences(payload: GenerateBracketsPayload): Boolean {
        return payload.stageDescriptors.all { sd ->
            if (!sd.inputDescriptor?.selectors.isNullOrEmpty()) {
                sd.inputDescriptor.selectors.all { sel -> !sel.applyToStageId.isNullOrBlank() && payload.stageDescriptors.any { stage -> stage.id == sel.applyToStageId } }
            } else {
                true
            }
        }
    }
}