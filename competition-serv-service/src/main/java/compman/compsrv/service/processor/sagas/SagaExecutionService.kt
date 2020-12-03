package compman.compsrv.service.processor.sagas

import arrow.core.Either
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.CommandProcessingError
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.command.AggregateServiceFactory
import compman.compsrv.service.processor.command.AggregatesWithEvents
import org.springframework.stereotype.Component

@Component
class SagaExecutionService(private val aggregateServiceFactory: AggregateServiceFactory) {
    fun executeSaga(commandDTO: CommandDTO, rocksDBOperations: DBOperations): Either<SagaExecutionError, AggregatesWithEvents<AbstractAggregate>> {
        val saga = processCommand(commandDTO).execute()
        return saga.accumulate(rocksDBOperations, aggregateServiceFactory).doRun()
    }
}