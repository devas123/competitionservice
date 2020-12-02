package compman.compsrv.service.processor.sagas

import arrow.core.Either
import compman.compsrv.errors.CommandProcessingError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.RocksDBOperations
import compman.compsrv.service.processor.command.AggregateServiceFactory
import org.springframework.stereotype.Component

@Component
class SagaExecutionService(private val aggregateServiceFactory: AggregateServiceFactory) {
    fun executeSaga(commandDTO: CommandDTO, rocksDBOperations: RocksDBOperations): Either<CommandProcessingError, Any> {
        val saga = processCommand(commandDTO)
            .step({ list: List<EventDTO> ->
                applyEvents(list)
            }, { commandProcessingError -> error(commandProcessingError) })
        return saga.failFast(rocksDBOperations, aggregateServiceFactory)
    }
}