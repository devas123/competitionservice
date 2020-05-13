package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.CompetitorGroupChange
import compman.compsrv.model.commands.payload.FightEditorApplyChangesPayload
import compman.compsrv.model.commands.payload.GroupChangeType
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class FightEditorApplyChangesPayloadValidator : AbstractCommandPayloadValidator<FightEditorApplyChangesPayload>(FightEditorApplyChangesPayload::class.java) {

    private fun Array<CompetitorGroupChange>.checkEveryCompetitorHasNotMoreThanOneChangePerGroup(): Boolean {
        val byCompetitorId = this.groupBy { it.competitorId }
        return byCompetitorId.mapValues { e ->
            e.value.size == e.value.distinctBy { it.groupId }.size
        }.values.reduce { acc, el -> acc && el }
    }

    private fun Array<CompetitorGroupChange>.checkEveryCompetitorHasNotMoreThanOneAddChange(): Boolean {
        val byCompetitorId = this.groupBy { it.competitorId }
        return byCompetitorId.mapValues { e ->
            e.value.filter { it.changeType == GroupChangeType.ADD }.size <= 1
        }.values.reduce { acc, el -> acc && el }
    }

    override fun <F> validateCommand(validationRules: PayloadValidationRules<F>, payload: FightEditorApplyChangesPayload, command: CommandDTO): Kind<F, FightEditorApplyChangesPayload> {
        return when {
            payload.stageId.isNullOrBlank() -> {
                validationRules.raiseError(PayloadValidationError.FieldMissing("stageId", command.id).nel())
            }
            payload.bracketsChanges.isNullOrEmpty() && payload.competitorGroupChanges.isNullOrEmpty() -> {
                validationRules.raiseError(PayloadValidationError.FieldMissing("bracketsChanges, competitorGroupChanges", command.id).nel())
            }
            !payload.bracketsChanges.isNullOrEmpty() && payload.bracketsChanges.any { bc -> bc.fightId.isNullOrBlank() || bc.competitors?.any { it.isNullOrBlank() } == true } -> {
                validationRules.raiseError(PayloadValidationError.GenericError("Brackets changes contain null fight ids or null competitor ids: ${payload.bracketsChanges}", command.id).nel())
            }
            !payload.competitorGroupChanges.isNullOrEmpty() && !payload.bracketsChanges.isNullOrEmpty() -> {
                validationRules.raiseError(PayloadValidationError.GenericError("Cannot have both fight and group changes.", command.id).nel())
            }
            !payload.competitorGroupChanges.isNullOrEmpty() && !payload.competitorGroupChanges.none { it.groupId.isNullOrBlank() || it.competitorId.isNullOrBlank() || it.changeType == null } -> {
                validationRules.raiseError(PayloadValidationError.GenericError("Some of the group changes do not have the required values.", command.id).nel())
            }
            !payload.competitorGroupChanges.isNullOrEmpty() && !payload.competitorGroupChanges.checkEveryCompetitorHasNotMoreThanOneChangePerGroup() -> {
                validationRules.raiseError(PayloadValidationError.GenericError("Competitor can be either added or removed from a group once, not both.", command.id).nel())
            }
            !payload.competitorGroupChanges.isNullOrEmpty() && !payload.competitorGroupChanges.checkEveryCompetitorHasNotMoreThanOneAddChange() -> {
                validationRules.raiseError(PayloadValidationError.GenericError("Competitor can be added to only one group.", command.id).nel())
            }
            else -> {
                validationRules.just(payload)
            }
        }
    }
}