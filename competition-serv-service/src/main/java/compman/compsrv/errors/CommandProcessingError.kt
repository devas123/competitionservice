package compman.compsrv.errors

import arrow.core.NonEmptyList
import arrow.typeclasses.Show
import compman.compsrv.util.PayloadValidationError



sealed class CommandProcessingError(val message: String) {
    data class CommandProcessingFailed(val e: Throwable): CommandProcessingError(e.message ?: "")
    class GenericError(m: String): CommandProcessingError(m)
    data class PayloadValidationFailed(val errors: NonEmptyList<PayloadValidationError>): CommandProcessingError(errors.show(Show.invoke { "Error: $error, failed on: $failedOn" }))
}
