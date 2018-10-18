package compman.compsrv.kafka.streams

import compman.compsrv.cluster.ZookeeperSession
import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.service.CategoryStateService
import compman.compsrv.validators.CategoryCommandsValidatorRegistry
import org.slf4j.LoggerFactory

class CategoryCommandExecutorTransformer(stateStoreName: String, private val categoryStateService: CategoryStateService,
                                         private val zookeeperSession: ZookeeperSession,
                                         private val validators: CategoryCommandsValidatorRegistry) : StateForwardingValueTransformer<CategoryState>(stateStoreName, CompetitionServiceTopics.CATEGORY_STATE_CHANGELOG_TOPIC_NAME) {
    override fun updateCorrelationId(currentState: CategoryState, command: Command): CategoryState = currentState.copy(correlationId = command.correlationId!!)

    override fun getStateKey(command: Command?): String = command?.categoryId ?: throw IllegalArgumentException("Category Id not provided. $command")


    companion object {
        private val log = LoggerFactory.getLogger(CategoryCommandExecutorTransformer::class.java)
    }

    private val mapper = ObjectMapperFactory.createObjectMapper()


    override fun doTransform(currentState: CategoryState?, command: Command?): Triple<String?, CategoryState?, List<EventHolder>?> {
        fun createEvent(type: EventType, payload: Map<String, Any?>?) =
                EventHolder(command?.correlationId ?: throw IllegalArgumentException("Command correlation Id is null"),
                        command.competitionId, command.categoryId, command.matId, type, payload)
        return try {
            log.info("Executing a command: $command, partition: ${context.partition()}, offset: ${context.offset()}")
            if (command?.categoryId != null) {
                val categoryId = command.categoryId!!
                val validationErrors = canExecuteCommand(currentState, command)
                if (validationErrors.isEmpty()) {
                    val (newState, events) = categoryStateService.executeCommand(command, currentState)
                    Triple(categoryId, newState, events)
                } else {
                    log.warn("Not executed, command validation failed.  \nCommand: $command. \nState: $currentState. \nPartition: ${context.partition()}. \nOffset: ${context.offset()}, errors: $validationErrors")
                    Triple(null, null, listOf(createEvent(EventType.ERROR_EVENT, mapOf("errors" to validationErrors))))
                }
            } else {
                log.warn("Did not execute because either command is null (${command == null}) or competition id is wrong: ${command?.competitionId}")
                Triple(null, null, listOf(createEvent(EventType.ERROR_EVENT,
                        mapOf("error" to "Did not execute command $command because either it is null (${command == null}) or competition id is wrong: ${command?.competitionId}"))))
            }
        } catch (e: Throwable) {
            log.error("Error while processing command: $command", e)
            Triple(null, null, listOf(createEvent(EventType.ERROR_EVENT, mapOf("error" to "${e.message}"))))
        }
    }

    private fun canExecuteCommand(state: CategoryState?, command: Command?): List<String> {
        /*(state == null || state.eventOffset < context.offset()) &&*/
        var errors = emptyList<String>()
        if (command != null) {
            errors += when (command.type) {
                CommandType.ADD_COMPETITOR_COMMAND -> {
                    val competitor = mapper.convertValue(command.payload, Competitor::class.java)
                    val allCompetitors = zookeeperSession.getCategoriesForCompetition(command.competitionId)?.mapNotNull { category ->
                        category.categoryId?.let {
                            zookeeperSession.getCategoryState(it)
                        }
                    }?.flatMap { it.competitors }
                    if (allCompetitors?.find { it.email == competitor.email } != null) {
                        listOf("Competitor with email: ${competitor.email} already registered")
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
        }

        return errors + validators.validate(command, state)
    }
}