package compman.compsrv.service.processor.competition.command

import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.util.Constants
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_COMMAND_EXECUTORS)
class DropAllBrackets : ICommandExecutor<Competition> {
    override fun execute(
        entity: Competition?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Competition> = entity?.let {
        if (entity.properties.bracketsPublished != true) {
            entity to entity.categories.map { cat ->
                AbstractAggregateService.createEvent(
                    command,
                    EventType.CATEGORY_BRACKETS_DROPPED,
                    command.payload
                ).apply { categoryId = cat }
            }
        } else {
            throw IllegalArgumentException("Brackets already published")
        }
    } ?: error(Constants.COMPETITION_NOT_FOUND)

    override val commandType: CommandType
        get() = CommandType.DROP_ALL_BRACKETS_COMMAND
}