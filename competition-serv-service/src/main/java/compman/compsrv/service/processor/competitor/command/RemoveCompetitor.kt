package compman.compsrv.service.processor.competitor.command

import compman.compsrv.aggregate.Competitor
import compman.compsrv.config.COMPETITOR_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorRemovedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITOR_COMMAND_EXECUTORS)
class RemoveCompetitor : ICommandExecutor<Competitor> {
    override fun execute(
            entity: Competitor,
            dbOperations: DBOperations,
            command: CommandDTO
    ): AggregateWithEvents<Competitor> {
        return if (dbOperations.fightsCount(entity.competitorDTO.categories.orEmpty().toList(), command.competitorId) <= 0) {
            entity to listOf(AbstractAggregateService.createEvent(command, EventType.COMPETITOR_REMOVED, CompetitorRemovedPayload(command.competitorId)))
        } else {
            throw IllegalStateException("There are already fights generated for the competitor. Please remove the fights first.")
        }
    }

    override val commandType: CommandType
        get() = CommandType.REMOVE_COMPETITOR_COMMAND
}