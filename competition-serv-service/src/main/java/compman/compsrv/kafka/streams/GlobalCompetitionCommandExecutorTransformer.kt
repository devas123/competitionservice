package compman.compsrv.kafka.streams

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.brackets.BracketDescriptor
import compman.compsrv.model.competition.Category
import compman.compsrv.model.competition.CompetitionProperties
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.schedule.ScheduleProperties
import compman.compsrv.service.ScheduleService
import compman.compsrv.service.StateQueryService
import org.apache.kafka.streams.kstream.ValueTransformer
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class GlobalCompetitionCommandExecutorTransformer(private val stateStoreName: String,
                                                  private val scheduleService: ScheduleService,
                                                  private val stateQueryService: StateQueryService,
                                                  private val mapper: ObjectMapper) : ValueTransformer<Command, EventHolder> {

    companion object {
        private val log = LoggerFactory.getLogger(GlobalCompetitionCommandExecutorTransformer::class.java)

    }

    private lateinit var stateStore: KeyValueStore<String, CompetitionProperties>
    private lateinit var context: ProcessorContext


    override fun init(context: ProcessorContext?) {
        this.context = context ?: throw IllegalStateException("Context cannot be null")
        stateStore = (context.getStateStore(stateStoreName)
                ?: throw IllegalStateException("Cannot get stateStore store $stateStoreName")) as KeyValueStore<String, CompetitionProperties>
    }

    private fun getProperties(competitionId: String): CompetitionProperties? {
        if (stateStore[competitionId] == null) {
            log.warn("There are no properties for competition $competitionId")
        }
        return stateStore[competitionId]
    }

    private fun getCategoryState(categoryId: String) = stateQueryService.getCategoryState(categoryId)


    private fun getAllBrackets(competitionId: String): List<BracketDescriptor> {
        val categories = stateQueryService.getCategories(competitionId)
        return categories.mapNotNull { it.categoryId }.mapNotNull {
            getCategoryState(it)?.brackets
        }
    }

    private fun getFightDurations(scheduleProperties: ScheduleProperties): Map<String, BigDecimal> {
        val categories = scheduleProperties.periodPropertiesList.flatMap { it.categories }
        return categories.map { it.categoryId!! to it.fightDuration }.toMap()
    }


    override fun transform(command: Command?): EventHolder? {
        fun createEvent(type: EventType, payload: Map<String, Any?>) = EventHolder(
                command?.competitionId ?: "null",
                command?.categoryId,
                command?.matId, type, payload)
                .setCommandOffset(context.offset())
                .setCommandPartition(context.partition())

        fun createErrorEvent(error: String) = EventHolder(command?.competitionId ?: "null",
                command?.categoryId,
                command?.matId,
                EventType.ERROR_EVENT, mapOf("error" to error))
                .setCommandOffset(context.offset())
                .setCommandPartition(context.partition())
        return try {
            log.info("Executing a command: $command, partition: ${context.partition()}, offset: ${context.offset()}")
            if (command != null) {
                val properties = getProperties(command.competitionId)
                if (canExecuteCommand(command)) {
                    val (newProperties, event) = executeCommand(command, properties, context.offset(), context.partition())
                    if (newProperties != null) {
                        stateStore.put(command.competitionId, newProperties)
                    } else {
                        stateStore.delete(command.competitionId)
                    }
                    event.setCommandOffset(context.offset())
                } else {
                    log.warn("Not executed: command is $command, state is $properties, partition: ${context.partition()}, offset: ${context.offset()}")
                    null
                }
            } else {
                log.warn("Did not execute because command is null")
                null
            }
        } catch (e: Throwable) {
            log.error("Error while processing command: $command", e)
            createErrorEvent("${e.message}")
        }
    }

    private fun canExecuteCommand(command: Command?): Boolean {
        return when (command?.type) {
            CommandType.CHECK_CATEGORY_OBSOLETE -> {
                true
            }
            CommandType.START_COMPETITION_COMMAND -> {
                val props = stateStore[command.competitionId]
                props?.status != CompetitionStatus.STARTED
            }
            CommandType.ADD_COMPETITOR_COMMAND -> {
                val props = stateStore[command.competitionId]
                val competitor = mapper.convertValue(command.payload, Competitor::class.java)
                !competitor?.email.isNullOrBlank() && props?.registeredIds?.contains(competitor.email) != true
            }
            CommandType.CREATE_COMPETITION_COMMAND -> this.stateStore[command.competitionId] == null
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> {
                return try {
                    val payload = command.payload!!
                    val newCategory = mapper.convertValue(payload["newCategory"], CategoryDTO::class.java)
                    val fighter = mapper.convertValue(payload["fighter"], Competitor::class.java)
                    newCategory != null && fighter != null && fighter.category.categoryId != newCategory.categoryId
                } catch (e: Exception) {
                    log.warn("Error while validating command: $command", e)
                    false
                }
            }
            else -> true
        }
    }

    private fun executeCommand(command: Command, properties: CompetitionProperties?, offset: Long, partition: Int): Pair<CompetitionProperties?, EventHolder> {
        fun createEvent(type: EventType, payload: Map<String, Any?>) = EventHolder(command.competitionId, command.categoryId
                ?: "null", command.matId, type, payload)
                .setCommandOffset(offset)
                .setCommandPartition(partition)

        fun createErrorEvent(error: String) = EventHolder(command.competitionId, command.categoryId
                ?: "null", command.matId, EventType.ERROR_EVENT, mapOf("error" to error))
                .setCommandOffset(offset)
                .setCommandPartition(partition)

        if (properties == null) {
            return when (command.type) {
                CommandType.CHECK_DASHBOARD_OBSOLETE -> {
                    null to createEvent(EventType.COMPETITION_DELETED, emptyMap())
                }
                CommandType.CREATE_COMPETITION_COMMAND -> {
                    val payload = command.payload!!
                    val tmpProps = CompetitionProperties(command.competitionId, payload["competitionName" +
                            ""].toString(), payload["creatorId"].toString())
                    val newproperties = tmpProps.applyProperties(payload)
                    newproperties to createEvent(EventType.COMPETITION_CREATED, mapOf("properties" to newproperties))
                }
                CommandType.CHECK_CATEGORY_OBSOLETE -> {
                    properties to createEvent(EventType.CATEGORY_DELETED, command.payload ?: emptyMap())
                }
                else -> {
                    if (command.type == CommandType.DELETE_COMPETITION_COMMAND) {
                        null to createEvent(EventType.COMPETITION_DELETED, emptyMap())
                    } else {
                        throw IllegalArgumentException("Cannot execute command because there is no such competition for id: ${command.competitionId}")
                    }
                }
            }
        } else {
            return when (command.type) {
                CommandType.CHECK_DASHBOARD_OBSOLETE -> {
                    properties to createEvent(EventType.DUMMY, emptyMap())
                }
                CommandType.CHECK_CATEGORY_OBSOLETE -> {
                    if (properties.categories?.any { it.categoryId == command.categoryId } != true) {
                        properties to createEvent(EventType.CATEGORY_DELETED, command.payload ?: emptyMap())
                    } else {
                        properties to createEvent(EventType.DUMMY, emptyMap())
                    }
                }
                CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> {
                    properties to createEvent(EventType.INTERNAL_COMPETITOR_CATEGORY_CHANGED, command.payload!!)
                }
                CommandType.DROP_ALL_BRACKETS_COMMAND -> {
                    if (!properties.bracketsPublished) {
                        val categories = stateQueryService.getCategories(command.competitionId).mapNotNull { it.categoryId }
                        val newPayload = (command.payload ?: emptyMap()) + ("categories" to categories)
                        properties to createEvent(EventType.INTERNAL_ALL_BRACKETS_DROPPED, newPayload)
                    } else {
                        properties to createErrorEvent("Cannot drop brackets, it is already published.")
                    }
                }
                CommandType.DROP_SCHEDULE_COMMAND -> {
                    if (!properties.schedulePublished) {
                        properties.setSchedule(null) to createEvent(EventType.SCHEDULE_DROPPED, command.payload
                                ?: emptyMap())
                    } else {
                        properties to createErrorEvent("Cannot drop schedule, it is already published.")
                    }
                }
                CommandType.ADD_COMPETITOR_COMMAND -> {
                    val competitor = mapper.convertValue(command.payload, Competitor::class.java)
                    properties.addRegisteredId(competitor?.email!!) to createEvent(EventType.INTERNAL_COMPETITOR_ADDED, command.payload!!)
                }
                CommandType.REMOVE_COMPETITOR_COMMAND -> {
                    val competitorId = command.payload?.get("competitorId")?.toString()
                    if (!competitorId.isNullOrBlank()) {
                        properties.deleteRegisteredId(competitorId!!) to createEvent(EventType.INTERNAL_COMPETITOR_REMOVED, command.payload!!)
                    } else {
                        properties to createEvent(EventType.ERROR_EVENT, mapOf("error" to "Competitor ID is empty."))
                    }
                }
                CommandType.GENERATE_SCHEDULE_COMMAND -> {
                    val payload = command.payload!!
                    val scheduleProperties = mapper.convertValue(payload, ScheduleProperties::class.java)
                    val schedule = scheduleService.generateSchedule(scheduleProperties, getAllBrackets(scheduleProperties.competitionId), getFightDurations(scheduleProperties))
                    val newProps = properties.setSchedule(schedule)
                    newProps to createEvent(EventType.SCHEDULE_GENERATED, mapOf("schedule" to schedule))
                }
                CommandType.UPDATE_COMPETITION_PROPERTIES_COMMAND -> {
                    val payload = command.payload!!
                    val newProps = properties.applyProperties(payload)
                    newProps to createEvent(EventType.COMPETITION_PROPERTIES_UPDATED, mapOf("properties" to newProps))
                }
                CommandType.ADD_CATEGORY_COMMAND -> {
                    val category = mapper.convertValue(command.payload, Category::class.java)
                    val cat = category.setCompetitionId(command.competitionId).setCategoryId(category.createId())
                    properties.addCategory(cat) to createEvent(EventType.CATEGORY_ADDED, mapOf("category" to cat))
                            .setCategoryId(cat.categoryId).setCompetitionId(cat.competitionId)
                }
                CommandType.DELETE_CATEGORY_COMMAND -> {
                    if (command.categoryId != null) {
                        val category = properties.categories?.firstOrNull { it.categoryId == command.categoryId }
                        properties.deleteCategory(command.categoryId!!) to createEvent(EventType.CATEGORY_DELETED, mapOf("categoryId" to category))
                    } else {
                        properties to createEvent(EventType.ERROR_EVENT, mapOf("error" to "Cannot delete, category id is null."))
                    }
                }
                CommandType.START_COMPETITION_COMMAND ->
                    properties.setStatus(CompetitionStatus.STARTED) to createEvent(EventType.COMPETITION_STARTED, mapOf("status" to CompetitionStatus.STARTED))
                CommandType.STOP_COMPETITION_COMMAND ->
                    properties.setStatus(CompetitionStatus.STOPPED) to createEvent(EventType.COMPETITION_STOPPED, mapOf("status" to CompetitionStatus.STOPPED))
                CommandType.PUBLISH_COMPETITION_COMMAND ->
                    properties.setStatus(CompetitionStatus.PUBLISHED) to createEvent(EventType.COMPETITION_PUBLISHED, mapOf("status" to CompetitionStatus.PUBLISHED))
                CommandType.UNPUBLISH_COMPETITION_COMMAND ->
                    properties.setStatus(CompetitionStatus.UNPUBLISHED) to createEvent(EventType.COMPETITION_UNPUBLISHED, mapOf("status" to CompetitionStatus.UNPUBLISHED))
                CommandType.DELETE_COMPETITION_COMMAND -> {
                    val categories = stateQueryService.getCategories(command.competitionId)
                    null to createEvent(EventType.COMPETITION_DELETED, mapOf("categories" to categories.mapNotNull { it.categoryId }))
                }
                else -> properties to createEvent(EventType.ERROR_EVENT, mapOf("error" to "Unknown or invalid command ${command.type}, properties: $properties"))
            }
        }
    }

    override fun close() {
        try {
            stateStore.close()
        } catch (e: Exception) {
            log.warn("Exception while closing store." +
                    "", e)
        }
    }

}