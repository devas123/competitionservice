package compman.compsrv.config.validators

import arrow.Kind
import arrow.core.nel
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.AddCompetitorPayload
import compman.compsrv.util.PayloadValidationError
import compman.compsrv.util.PayloadValidationRules
import org.springframework.stereotype.Component

@Component
class AddCompetitorPayloadValidator :
    AbstractCommandPayloadValidator<AddCompetitorPayload>(AddCompetitorPayload::class.java) {
    override fun <F> validateCommand(
        validationRules: PayloadValidationRules<F>,
        payload: AddCompetitorPayload,
        command: CommandDTO
    ): Kind<F, AddCompetitorPayload> {
        return when (payload.competitor) {
            null -> validationRules.raiseError(
                PayloadValidationError.FieldMissing(
                    "competitor",
                    command.id
                ).nel()
            )
            else -> {
                validationRules.just(payload)
            }
        }
    }
}