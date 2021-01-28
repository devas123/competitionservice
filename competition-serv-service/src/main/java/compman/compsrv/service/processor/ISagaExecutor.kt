package compman.compsrv.service.processor

import arrow.core.Either
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.DBOperations

interface ISagaExecutor {
    fun executeSaga(dbOperations: DBOperations, command: CommandDTO): Either<SagaExecutionError, List<EventDTO>>
    val commandType: CommandType
}