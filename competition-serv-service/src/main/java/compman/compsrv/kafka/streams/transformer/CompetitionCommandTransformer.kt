package compman.compsrv.kafka.streams.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.dto.competition.CompetitionStateSnapshot
import compman.compsrv.repository.CompetitionStateRepository
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.resolver.CompetitionStateResolver
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate

class CompetitionCommandTransformer(competitionStateService: CompetitionStateService,
                                    private val competitionStateRepository: CompetitionStateRepository,
                                    private val competitionStateResolver: CompetitionStateResolver,
                                    transactionTemplate: TransactionTemplate,
                                    private val mapper: ObjectMapper,
                                    clusterSession: ClusterSession,
                                    private val snapshotStoreName: String)
    : AbstractCommandTransformer(competitionStateService, transactionTemplate, clusterSession, mapper) {

    private lateinit var snapshotStore: KeyValueStore<String, CompetitionStateSnapshot>

    override fun init(context: ProcessorContext?) {
        super.init(context)
        snapshotStore = context?.getStateStore(snapshotStoreName) as KeyValueStore<String, CompetitionStateSnapshot>
    }

    override fun initState(id: String, timestamp: Long) {
        competitionStateResolver.resolveLatestCompetitionState(id, timestamp) { _id -> snapshotStore.get(_id) }
    }

    override fun getState(id: String) = competitionStateRepository.findById(id)

    override fun close() {
    }

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionCommandTransformer::class.java)
    }

    override fun canExecuteCommand(command: CommandDTO?): List<String> {
        return when (command?.type) {
            CommandType.CREATE_COMPETITION_COMMAND -> if (competitionStateRepository.existsById(command.competitionId)) {
                listOf("Competition already exists.")
            } else {
                emptyList()
            }
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> {
                return try {
                    val payload = mapper.convertValue(command.payload, ChangeCompetitorCategoryPayload::class.java)
                    val newCategory = payload.newCategory
                    val fighter = payload.fighter
                    if (fighter.categoryId != newCategory.id) {
                        emptyList()
                    } else {
                        listOf("New category is null or fighter is null or the source and the target categories are the same.")
                    }
                } catch (e: Exception) {
                    log.warn("Error while validating command: $command", e)
                    listOf("Error while validating command $command: $e")
                }
            }
            else -> emptyList()
        }
    }
}