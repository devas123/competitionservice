package compman.compsrv.kafka.streams

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.competition.CompetitionProperties
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.service.CompetitionPropertiesService
import org.slf4j.LoggerFactory

class GlobalCompetitionCommandExecutorTransformer(stateStoreName: String,
                                                  competitionPropertiesService: CompetitionPropertiesService,
                                                  private val mapper: ObjectMapper)
    : StateForwardingValueTransformer<CompetitionProperties>(stateStoreName, CompetitionServiceTopics.COMPETITION_STATE_CHANGELOG_TOPIC_NAME, competitionPropertiesService) {

    override fun getStateKey(command: Command?): String = command?.competitionId
            ?: throw IllegalArgumentException("No competition Id. $command")

    companion object {
        private val log = LoggerFactory.getLogger(GlobalCompetitionCommandExecutorTransformer::class.java)
    }

    override fun canExecuteCommand(state: CompetitionProperties?, command: Command?): List<String>{
        return when (command?.type) {
            CommandType.CHECK_CATEGORY_OBSOLETE -> {
                emptyList()
            }
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
                    if (newCategory != null && fighter != null && fighter.category.categoryId != newCategory.categoryId) {
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