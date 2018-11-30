package compman.compsrv.kafka.streams.processor

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.model.competition.CompetitionState
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.repository.CompetitionStateRepository
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.resolver.CompetitionStateResolver
import org.slf4j.LoggerFactory
import java.util.*
import javax.persistence.OptimisticLockException

class CompetitionCommandTransformer(competitionStateService: CompetitionStateService,
                                    private val competitionStateRepository: CompetitionStateRepository,
                                    private val competitionStateResolver: CompetitionStateResolver,
                                    private val mapper: ObjectMapper,
                                    clusterSession: ClusterSession)
    : StateForwardingCommandTransformer(competitionStateService, clusterSession, mapper) {


    override fun getState(id: String): Optional<CompetitionState> {
        return competitionStateResolver.resolveLatestCompetitionState(id)
    }

    override fun saveState(readOnlyKey: String, state: CompetitionState) {
        var success = false
        while (!success) {
            success = try {
                competitionStateRepository.save(state)
                true
            } catch (e: OptimisticLockException) {
                false
            }
        }
    }

    override fun deleteState(id: String) {
        competitionStateRepository.delete(id)
    }

    override fun close() {
    }

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionCommandTransformer::class.java)
    }

    override fun canExecuteCommand(state: CompetitionState?, command: Command?): List<String> {
        return when (command?.type) {
            CommandType.START_COMPETITION_COMMAND -> {
                if (state?.status != CompetitionStatus.STARTED) {
                    emptyList()
                } else {
                    listOf("Competition already started")
                }
            }
            CommandType.CREATE_COMPETITION_COMMAND -> if (state == null) {
                listOf("State is missing")
            } else {
                emptyList()
            }
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> {
                return try {
                    val payload = command.payload!!
                    val newCategory = mapper.convertValue(payload["newCategory"], CategoryDTO::class.java)
                    val fighter = mapper.convertValue(payload["fighter"], Competitor::class.java)
                    if (newCategory != null && fighter != null && fighter.categoryId != newCategory.categoryId) {
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