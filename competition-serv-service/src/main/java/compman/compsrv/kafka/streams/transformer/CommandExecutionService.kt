package compman.compsrv.kafka.streams.transformer

import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.repository.RocksDBRepository
import compman.compsrv.service.CommandSyncExecutor
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.resolver.CompetitionStateResolver

open class CommandExecutionService(
    competitionStateService: CompetitionStateService,
    competitionStateResolver: CompetitionStateResolver,
    clusterOperations: ClusterOperations,
    commandSyncExecutor: CommandSyncExecutor,
    competitionId: String, rocksDBRepository: RocksDBRepository
) : AbstractCommandExecutionService(competitionStateService, clusterOperations, commandSyncExecutor) {

    init {
        competitionStateResolver.resolveLatestCompetitionState(competitionId, rocksDBRepository.getOperations())
    }
}