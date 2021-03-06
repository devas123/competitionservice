package compman.compsrv.service.processor.competition.command

import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitionStatusUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_COMMAND_EXECUTORS)
class UnpublishCompetition : ICommandExecutor<Competition> {
    override fun execute(
        entity: Competition?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Competition> {
        return entity to listOf(
            AbstractAggregateService.createEvent(
                command,
                EventType.COMPETITION_UNPUBLISHED,
                CompetitionStatusUpdatedPayload(CompetitionStatus.UNPUBLISHED)
            )
        )
    }

    override val commandType: CommandType
        get() = CommandType.UNPUBLISH_COMPETITION_COMMAND
}