package compman.compsrv.kafka.streams.transformer

import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.service.CommandSyncExecutor
import compman.compsrv.service.ICommandProcessingService
import compman.compsrv.service.processor.event.IEventExecutionEffects
import compman.compsrv.service.resolver.CompetitionStateResolver
import java.util.concurrent.ConcurrentHashMap

class CompetitionCommandTransformerFactory(private val competitionStateService: ICommandProcessingService<CommandDTO, EventDTO>,
                                           private val competitionStateRepository: CompetitionPropertiesDao,
                                           private val competitionStateResolver: CompetitionStateResolver,
                                           private val eventExecutionEffects: IEventExecutionEffects,
                                           private val clusterOperations: ClusterOperations,
                                           private val commandSyncExecutor: CommandSyncExecutor,
                                           private val mapper: ObjectMapper) {

    private val commandTransformers: ConcurrentHashMap<String, CompetitionCommandTransformer> = ConcurrentHashMap()

    fun getCompetitionCommandTransformer(competitionId: String): CompetitionCommandTransformer {
        return commandTransformers.computeIfAbsent(competitionId) {
            CompetitionCommandTransformer(competitionStateService, competitionStateRepository, competitionStateResolver, eventExecutionEffects, clusterOperations, commandSyncExecutor, mapper, it)
        }
    }
}