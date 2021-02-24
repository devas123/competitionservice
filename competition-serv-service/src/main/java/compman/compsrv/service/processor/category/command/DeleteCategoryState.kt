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
class DeleteCategoryState : ICommandExecutor<Category> {
    override fun execute(
        entity: Category?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Category> =
        if (dbOperations.getCategoryCompetitors(command.categoryId, false)
                .isNullOrEmpty() && entity?.fights.isNullOrEmpty()
        ) {
            entity to listOf(
                AbstractAggregateService.createEvent(
                    command,
                    EventType.CATEGORY_DELETED,
                    command.payload
                )
            )
        } else {
            throw IllegalArgumentException("There are already competitors registered to this category. Please move them to another category first.")
        }


    override val commandType: CommandType
        get() = CommandType.DELETE_CATEGORY_COMMAND
}