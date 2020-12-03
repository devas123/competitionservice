package compman.compsrv.errors

import arrow.core.NonEmptyList
import arrow.extension
import arrow.typeclasses.Show
import compman.compsrv.util.PayloadValidationError
import java.io.PrintWriter
import java.io.StringWriter


sealed class SagaExecutionError {
    data class CommandProcessingFailed(val e: Throwable): SagaExecutionError()
    data class EventApplicationFailed(val e: Throwable): SagaExecutionError()
    data class GenericError(val m: String): SagaExecutionError()
    data class PayloadValidationFailed(val errors: NonEmptyList<PayloadValidationError>)
}

fun stackTraceToStr(e: Throwable): String {
    val sw = StringWriter()
    e.printStackTrace(PrintWriter(sw))
    return sw.buffer.toString()
}

fun SagaExecutionError.show(): String {
    return when(this) {
        is SagaExecutionError.CommandProcessingFailed -> "Command processing failed: \n${stackTraceToStr(e)}"
        is SagaExecutionError.EventApplicationFailed -> "Event processing failed: \n${stackTraceToStr(e)}"
        is SagaExecutionError.GenericError -> "An error occurred: ${this.m}"
    }
}