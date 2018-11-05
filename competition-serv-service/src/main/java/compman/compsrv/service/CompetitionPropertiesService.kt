package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.brackets.BracketDescriptor
import compman.compsrv.model.competition.CompetitionProperties
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.schedule.Schedule
import compman.compsrv.model.schedule.ScheduleProperties
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class CompetitionPropertiesService(private val scheduleService: ScheduleService,
                                   private val stateQueryService: StateQueryService,
                                   private val mapper: ObjectMapper) : ICommandProcessingService<CompetitionProperties, Command, EventHolder> {

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

    private fun <T> getPayloadAs(payload: Any?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.convertValue(payload, clazz)
        }
        return null
    }

    override fun apply(event: EventHolder, state: CompetitionProperties?): Pair<CompetitionProperties?, List<EventHolder>> {
        return try {
            when (event.type) {
                EventType.COMPETITION_DELETED -> {
                    state?.copy(status = CompetitionStatus.DELETED) to listOf(event)
                }
                EventType.COMPETITION_CREATED -> {
                    val newstate = getPayloadAs(event.payload?.get("state"), CompetitionProperties::class.java)
                    newstate to listOf(event)
                }
                EventType.DUMMY -> {
                    state to emptyList()
                }
                in listOf(EventType.INTERNAL_COMPETITOR_CATEGORY_CHANGED, EventType.INTERNAL_ALL_BRACKETS_DROPPED) -> {
                    state to listOf(event)
                }
                EventType.SCHEDULE_DROPPED -> {
                    state?.setSchedule(null) to listOf(event)
                }
                EventType.SCHEDULE_GENERATED -> {
                    val schedule = getPayloadAs(event.payload?.get("schedule"), Schedule::class.java)
                    val newProps = state?.setSchedule(schedule)
                    newProps to listOf(event)
                }
                EventType.COMPETITION_PROPERTIES_UPDATED -> {
                    val payload = event.payload!!
                    val newProps = state?.applyProperties(payload)
                    newProps to listOf(event)
                }
                in listOf(EventType.COMPETITION_STARTED, EventType.COMPETITION_STOPPED, EventType.COMPETITION_PUBLISHED, EventType.COMPETITION_UNPUBLISHED) -> {
                    val status = getPayloadAs(event.payload?.get("status"), CompetitionStatus::class.java)
                    if (status != null) {
                        state?.setStatus(status) to listOf(event)
                    } else {
                        state to emptyList()
                    }
                }
                else -> {
                    state to listOf(event)
                }
            }
        } catch (e: Exception) {
            state to emptyList()
        }
    }

    override fun process(command: Command, state: CompetitionProperties?): List<EventHolder> {
        fun executeCommand(command: Command, properties: CompetitionProperties?): EventHolder {
            fun createEvent(type: EventType, payload: Map<String, Any?>) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId
                    ?: "null", command.matId, type, payload)

            fun createErrorEvent(error: String) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId
                    ?: "null", command.matId, EventType.ERROR_EVENT, mapOf("error" to error))

            return when (command.type) {
                CommandType.CHECK_DASHBOARD_OBSOLETE -> {
                    if (properties == null) {
                        createEvent(EventType.COMPETITION_DELETED, emptyMap())
                    } else {
                        createEvent(EventType.DUMMY, emptyMap())
                    }
                }
                CommandType.CREATE_COMPETITION_COMMAND -> {
                    val payload = command.payload!!
                    val tmpProps = CompetitionProperties(command.correlationId!!, command.competitionId, payload["competitionName" +
                            ""].toString(), payload["creatorId"].toString())
                    val newproperties = tmpProps.applyProperties(payload)
                    createEvent(EventType.COMPETITION_CREATED, mapOf("properties" to newproperties))
                }
                CommandType.CHECK_CATEGORY_OBSOLETE -> {
                    if (properties == null) {
                        createEvent(EventType.CATEGORY_DELETED, command.payload ?: emptyMap())
                    } else {
                        createEvent(EventType.DUMMY, emptyMap())
                    }
                }
                CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> {
                    createEvent(EventType.INTERNAL_COMPETITOR_CATEGORY_CHANGED, command.payload!!)
                }
                CommandType.DROP_ALL_BRACKETS_COMMAND -> {
                    if (properties?.bracketsPublished != true) {
                        val categories = stateQueryService.getCategories(command.competitionId).mapNotNull { it.categoryId }
                        val newPayload = (command.payload ?: emptyMap()) + ("categories" to categories)
                        createEvent(EventType.INTERNAL_ALL_BRACKETS_DROPPED, newPayload)
                    } else {
                        createErrorEvent("Cannot drop brackets, it is already published.")
                    }
                }
                CommandType.DROP_SCHEDULE_COMMAND -> {
                    if (properties?.schedulePublished != true) {
                        createEvent(EventType.SCHEDULE_DROPPED, command.payload
                                ?: emptyMap())
                    } else {
                        createErrorEvent("Cannot drop schedule, it is already published.")
                    }
                }
                CommandType.GENERATE_SCHEDULE_COMMAND -> {
                    val payload = command.payload!!
                    val scheduleProperties = mapper.convertValue(payload, ScheduleProperties::class.java)
                    val schedule = scheduleService.generateSchedule(scheduleProperties, getAllBrackets(scheduleProperties.competitionId), getFightDurations(scheduleProperties))
                    createEvent(EventType.SCHEDULE_GENERATED, mapOf("schedule" to schedule))
                }
                CommandType.UPDATE_COMPETITION_PROPERTIES_COMMAND -> {
                    createEvent(EventType.COMPETITION_PROPERTIES_UPDATED, command.payload!!)
                }
                CommandType.START_COMPETITION_COMMAND -> {
                    createEvent(EventType.COMPETITION_STARTED, mapOf("status" to CompetitionStatus.STARTED))
                }
                CommandType.STOP_COMPETITION_COMMAND -> {
                    createEvent(EventType.COMPETITION_STOPPED, mapOf("status" to CompetitionStatus.STOPPED))
                }
                CommandType.PUBLISH_COMPETITION_COMMAND -> {
                    createEvent(EventType.COMPETITION_PUBLISHED, mapOf("status" to CompetitionStatus.PUBLISHED))
                }
                CommandType.UNPUBLISH_COMPETITION_COMMAND -> {
                    createEvent(EventType.COMPETITION_UNPUBLISHED, mapOf("status" to CompetitionStatus.UNPUBLISHED))
                }
                CommandType.DELETE_COMPETITION_COMMAND -> {
                    val categories = stateQueryService.getCategories(command.competitionId)
                    createEvent(EventType.COMPETITION_DELETED, mapOf("categories" to categories.mapNotNull { it.categoryId }))
                }
                else -> {
                    createEvent(EventType.ERROR_EVENT, mapOf("error" to "Unknown or invalid command ${command.type}, properties: $properties"))
                }
            }
        }
        return listOf(executeCommand(command, state))
    }

}