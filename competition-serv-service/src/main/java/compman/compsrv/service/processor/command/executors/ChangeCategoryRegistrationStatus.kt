package compman.compsrv.service.processor.command.executors

import compman.compsrv.aggregate.Category
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.command.AbstractAggregateService
import compman.compsrv.service.processor.command.AggregateWithEvents
import compman.compsrv.service.processor.command.ICommandExecutor
import org.springframework.stereotype.Component

@Component
class ChangeCategoryRegistrationStatus : ICommandExecutor<Category> {
    override fun execute(
        entity: Category,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Category> =
        entity to listOf(
            AbstractAggregateService.createEvent(
                command,
                EventType.CATEGORY_REGISTRATION_STATUS_CHANGED,
                command.payload
            )
        )

    override val commandType: CommandType
        get() = CommandType.CHANGE_CATEGORY_REGISTRATION_STATUS_COMMAND
}