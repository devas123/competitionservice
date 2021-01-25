package compman.compsrv.service.processor.command

import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.repository.DBOperations

interface ICommandExecutor<EntityType> {
    fun execute(entity: EntityType, dbOperations: DBOperations, command: CommandDTO): AggregateWithEvents<EntityType>
    val commandType: CommandType
}