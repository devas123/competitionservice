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
import org.apache.kafka.streams.kstream.ValueTransformer
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.processor.PunctuationType
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory

class CategoryCommandExecutorTransformer(private val stateStoreName: String, private val categoryStateService: CategoryStateService,
                                         private val zookeeperSession: ZookeeperSession,
                                         private val validators: CategoryCommandsValidatorRegistry) : ValueTransformer<Command, Array<EventHolder>> {

    companion object {
        private val log = LoggerFactory.getLogger(CategoryCommandExecutorTransformer::class.java)
    }

    private lateinit var stateStore: KeyValueStore<String, CategoryState>
    private lateinit var context: ProcessorContext
    private val mapper = ObjectMapperFactory.createObjectMapper()

    override fun init(context: ProcessorContext?) {
        this.context = context ?: throw IllegalStateException("Context cannot be null")
        stateStore = (context.getStateStore(stateStoreName)
                ?: throw IllegalStateException("Cannot get stateStore store $stateStoreName")) as KeyValueStore<String, CategoryState>
    }


    override fun transform(command: Command?): Array<EventHolder> {
        fun createEvent(type: EventType, payload: Map<String, Any?>?) =
                EventHolder(command?.competitionId ?: "null", command?.categoryId, command?.matId, type, payload)
                        .setCommandPartition(context.partition())
                        .setCommandOffset(context.offset())
        return try {
            log.info("Executing a command: $command, partition: ${context.partition()}, offset: ${context.offset()}")
            if (command?.categoryId != null) {
                val categoryId = command.categoryId!!
                val state = stateStore.get(categoryId)
                val validationErrors = canExecuteCommand(state, command)
                if (validationErrors.isEmpty()) {
                    val (newState, events) = categoryStateService.executeCommand(command, state)
                    if (events.any { it.type != EventType.ERROR_EVENT } && (newState != null || events.any { it.type == EventType.CATEGORY_STATE_DELETED })) {
                        stateStore.put(categoryId, newState?.withEventOffset(context.offset())?.withEventPartition(context.partition()))
                    }
                    events.toTypedArray()
                } else {
                    log.warn("Not executed, command validation failed.  \nCommand: $command. \nState: $state. \nPartition: ${context.partition()}. \nOffset: ${context.offset()}, errors: $validationErrors")
                    arrayOf(createEvent(EventType.ERROR_EVENT, mapOf("errors" to validationErrors)))
                }
            } else {
                log.warn("Did not execute because either command is null (${command == null}) or competition id is wrong: ${command?.competitionId}")
                arrayOf(createEvent(EventType.ERROR_EVENT,
                        mapOf("error" to "Did not execute command $command because either it is null (${command == null}) or competition id is wrong: ${command?.competitionId}")))
            }
        } catch (e: Throwable) {
            log.error("Error while processing command: $command", e)
            arrayOf(createEvent(EventType.ERROR_EVENT, mapOf("error" to "${e.message}")))
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

    override fun close() {
        try {
            stateStore.close()
        } catch (e: Exception) {
            log.warn("Error while closing store.", e)
        }
    }

}