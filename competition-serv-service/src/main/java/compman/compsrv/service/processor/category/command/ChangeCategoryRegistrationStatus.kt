package compman.compsrv.service.processor.category.command

import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_COMMAND_EXECUTORS)
class ChangeCategoryRegistrationStatus : ICommandExecutor<Category> {
    override fun execute(
        entity: Category?,
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