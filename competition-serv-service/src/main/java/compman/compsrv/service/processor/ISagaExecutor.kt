package compman.compsrv.service.processor

import arrow.core.Either
import arrow.core.flatMap
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.saga.SagaStep
import compman.compsrv.service.processor.saga.accumulate
import compman.compsrv.service.processor.saga.doRun

interface ISagaExecutor {
    fun createSaga(dbOperations: DBOperations, command: CommandDTO): Either<SagaExecutionError, SagaStep<List<EventDTO>>>
    fun runSaga(dbOperations: DBOperations, saga: Either<SagaExecutionError, SagaStep<List<EventDTO>>>): Either<SagaExecutionError, List<EventDTO>> =
        saga.flatMap { it.accumulate(dbOperations, delegatingAggregateService).doRun() }
    val commandType: CommandType
    val delegatingAggregateService: DelegatingAggregateService
}