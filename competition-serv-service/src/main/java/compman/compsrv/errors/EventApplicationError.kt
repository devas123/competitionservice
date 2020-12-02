package compman.compsrv.errors

import arrow.core.NonEmptyList
import arrow.typeclasses.Show
import compman.compsrv.util.PayloadValidationError



sealed class EventApplicationError(val message: String) {
    data class EventApplicationFailed(val e: Exception): EventApplicationError(e.message ?: "")
    data class PayloadValidationFailed(val errors: NonEmptyList<PayloadValidationError>): EventApplicationError(errors.show(Show.invoke { "Error: $error, failed on: $failedOn" }))
}
