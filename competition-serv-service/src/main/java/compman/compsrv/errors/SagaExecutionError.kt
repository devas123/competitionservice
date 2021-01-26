package compman.compsrv.errors

import arrow.core.NonEmptyList
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.util.PayloadValidationError
import java.io.PrintWriter
import java.io.StringWriter


sealed class SagaExecutionError {
    data class CommandProcessingFailed(val e: Throwable): SagaExecutionError()
    data class EventApplicationFailed(val e: Throwable): SagaExecutionError()
    data class GenericError(val m: String): SagaExecutionError()
    data class PayloadValidationFailed(val errors: NonEmptyList<PayloadValidationError>): SagaExecutionError()
    data class ErrorWithCompensatingActions(val error: SagaExecutionError, val actions: List<AggregateWithEvents<AbstractAggregate>>, val compensatingActions: List<AggregateWithEvents<AbstractAggregate>>): SagaExecutionError()
}

fun stackTraceToStr(e: Throwable): String {
    val sw = StringWriter()
    e.printStackTrace(PrintWriter(sw))
    return sw.buffer.toString()
}

fun SagaExecutionError.getEvents(): List<AggregateWithEvents<AbstractAggregate>> =
    when(this) {
        is SagaExecutionError.CommandProcessingFailed -> emptyList()
        is SagaExecutionError.EventApplicationFailed -> emptyList()
        is SagaExecutionError.GenericError -> emptyList()
        is SagaExecutionError.PayloadValidationFailed -> emptyList()
        is SagaExecutionError.ErrorWithCompensatingActions -> error.getEvents() + actions + compensatingActions
    }

fun SagaExecutionError.getCompensatingActions(): List<AggregateWithEvents<AbstractAggregate>> =
    when(this) {
        is SagaExecutionError.CommandProcessingFailed -> emptyList()
        is SagaExecutionError.EventApplicationFailed -> emptyList()
        is SagaExecutionError.GenericError -> emptyList()
        is SagaExecutionError.PayloadValidationFailed -> emptyList()
        is SagaExecutionError.ErrorWithCompensatingActions -> error.getCompensatingActions() + compensatingActions
    }


fun SagaExecutionError.show(): String {
    return when(this) {
        is SagaExecutionError.CommandProcessingFailed -> "Command processing failed: \n${stackTraceToStr(e)}"
        is SagaExecutionError.EventApplicationFailed -> "Event processing failed: \n${stackTraceToStr(e)}"
        is SagaExecutionError.GenericError -> "An error occurred: ${this.m}"
        is SagaExecutionError.PayloadValidationFailed -> "Payload validation failed: ${this.errors}"
        is SagaExecutionError.ErrorWithCompensatingActions -> "There was an error: [${error.show()}] and compensating actions were taken: ${this.getCompensatingActions()}"
    }
}