package compman.compsrv.service.processor.competition.command

import compman.compsrv.aggregate.Competition
import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_COMMAND_EXECUTORS)
class SendProcessingInfo(private val clusterOperations: ObjectProvider<ClusterOperations>) : ICommandExecutor<Competition> {
    override fun execute(
        entity: Competition,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Competition> {
        return entity to clusterOperations.ifAvailable?.createProcessingInfoEvents(command.correlationId, setOf(command.competitionId))
                ?.toList().orEmpty()
    }

    override val commandType: CommandType
        get() = CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND
}