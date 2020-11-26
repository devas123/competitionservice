package compman.compsrv.kafka.streams.transformer

import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.service.CommandSyncExecutor
import compman.compsrv.service.ICommandProcessingService
import compman.compsrv.service.processor.event.IEventExecutionEffects
import compman.compsrv.service.resolver.CompetitionStateResolver
import org.slf4j.LoggerFactory

open class CompetitionCommandTransformer(competitionStateService: ICommandProcessingService<CommandDTO, EventDTO>,
                                         private val competitionStateRepository: CompetitionPropertiesDao,
                                         competitionStateResolver: CompetitionStateResolver,
                                         eventExecutionEffects: IEventExecutionEffects,
                                         clusterOperations: ClusterOperations,
                                         commandSyncExecutor: CommandSyncExecutor,
                                         mapper: ObjectMapper, private val competitionId: String)
    : AbstractCommandTransformer(competitionStateService, eventExecutionEffects, clusterOperations, commandSyncExecutor, mapper) {

    init {
        competitionStateResolver.resolveLatestCompetitionState(competitionId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionCommandTransformer::class.java)
    }

    override fun canExecuteCommand(command: CommandDTO?): List<String> {
        if (command?.competitionId != competitionId) {
            return listOf("Command has wrong competition id: ${command?.competitionId}, should be $competitionId.")
        }
        return when (command.type) {
            CommandType.CREATE_COMPETITION_COMMAND -> if (competitionStateRepository.existsById(command.competitionId)) {
                log.warn("Competition ${command.competitionId} already exists.")
                listOf("Competition already exists.")
            } else {
                emptyList()
            }
            else -> emptyList()
        }
    }
}