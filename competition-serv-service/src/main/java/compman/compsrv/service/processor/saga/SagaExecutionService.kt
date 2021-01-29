package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.left
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.ISagaExecutor
import org.springframework.stereotype.Component

@Component
class SagaExecutionService(executors: List<ISagaExecutor>) {
    private val executorsByType = executors.groupBy { it.commandType }.mapValues { it.value.first() }
    fun executeSaga(
        c: CommandDTO,
        rocksDBOperations: DBOperations
    ): Either<SagaExecutionError, List<EventDTO>> {
        val executor = executorsByType[c.type]
        return executor?.let { it.runSaga(rocksDBOperations, it.createSaga(rocksDBOperations, c)) }
            ?: SagaExecutionError.GenericError("Unknown command type: ${c.type}").left()
    }
}