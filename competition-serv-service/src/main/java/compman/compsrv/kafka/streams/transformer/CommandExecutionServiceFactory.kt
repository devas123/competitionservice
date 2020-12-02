package compman.compsrv.kafka.streams.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.repository.RocksDBRepository
import compman.compsrv.service.CommandSyncExecutor
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.resolver.CompetitionStateResolver
import java.util.concurrent.ConcurrentHashMap

class CommandExecutionServiceFactory(private val competitionStateService: CompetitionStateService,
                                     private val competitionStateResolver: CompetitionStateResolver,
                                     private val clusterOperations: ClusterOperations,
                                     private val commandSyncExecutor: CommandSyncExecutor,
                                     private val rocksDBRepository: RocksDBRepository,
                                     private val mapper: ObjectMapper) {

    private val commandTransformers: ConcurrentHashMap<String, CommandExecutionService> = ConcurrentHashMap()

    fun getCompetitionCommandTransformer(competitionId: String): CommandExecutionService {
        return commandTransformers.computeIfAbsent(competitionId) {
            CommandExecutionService(competitionStateService, competitionStateResolver, clusterOperations, commandSyncExecutor, mapper, it, rocksDBRepository)
        }
    }
}