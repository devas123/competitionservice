package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.brackets.BracketDescriptor
import compman.compsrv.model.competition.CompetitionProperties
import compman.compsrv.model.competition.CompetitionState
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.es.events.payload.CompetitionPropertiesUpdatedPayload
import compman.compsrv.model.schedule.Schedule
import compman.compsrv.model.schedule.ScheduleProperties
import compman.compsrv.repository.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class CompetitionStateService(private val scheduleService: ScheduleService,
                              private val stateQueryService: StateQueryService,
                              private val fightRepository: FightCrudRepository,
                              private val competitorCrudRepository: CompetitorCrudRepository,
                              private val categoryCrudRepository: CategoryCrudRepository,
                              private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                              private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                              private val bracketsCrudRepository: BracketsCrudRepository,
                              private val mapper: ObjectMapper) : ICommandProcessingService<CompetitionState, Command, EventHolder> {

    private fun getAllBrackets(competitionId: String): List<BracketDescriptor> {
        val competitionState = competitionStateCrudRepository.findById(competitionId)
        return competitionState.map { t: CompetitionState? ->
            t?.categories?.mapNotNull { it.brackets }
        }?.orElse(emptyList()) ?: emptyList()
    }

    private fun getFightDurations(scheduleProperties: ScheduleProperties): Map<String, BigDecimal> {
        val categories = scheduleProperties.periodPropertiesList.flatMap { it.categories }
        return categories.map { it.id to it.fightDuration }.toMap()
    }

    private fun <T> getPayloadAs(payload: Any?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.convertValue(payload, clazz)
        }
        return null
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun apply(event: EventHolder, state: CompetitionState?): Pair<CompetitionState?, List<EventHolder>> {
        fun createErrorEvent(error: String) = EventHolder(event.correlationId, event.competitionId, event.categoryId
                ?: "null", event.matId, EventType.ERROR_EVENT, mapper.writeValueAsBytes(mapOf("error" to error)))
        return try {
            val ns = when (event.type) {
                EventType.COMPETITION_DELETED -> {
                    state?.copy(status = CompetitionStatus.DELETED) to listOf(event)

                }
                EventType.COMPETITION_CREATED -> {
                    val newstate = getPayloadAs(event.payload, CompetitionState::class.java)
                    newstate to listOf(event)
                }
                EventType.DUMMY -> {
                    state to emptyList()
                }
                in listOf(EventType.INTERNAL_COMPETITOR_CATEGORY_CHANGED, EventType.DROP_ALL_BRACKETS_SAGA_STARTED) -> {
                    state to listOf(event)
                }
                EventType.SCHEDULE_DROPPED -> {
                    state?.withSchedule(null) to listOf(event)
                }
                EventType.SCHEDULE_GENERATED -> {
                    val schedule = getPayloadAs(event.payload, Schedule::class.java)
                    val newProps = state?.withSchedule(schedule)
                    newProps to listOf(event)
                }
                EventType.COMPETITION_PROPERTIES_UPDATED -> {
                    val payload = getPayloadAs(event.payload, CompetitionPropertiesUpdatedPayload::class.java)
                    val newProps = state?.withProperties(state.properties.applyProperties(payload?.properties))
                    newProps to listOf(event)
                }
                in listOf(EventType.COMPETITION_STARTED, EventType.COMPETITION_STOPPED, EventType.COMPETITION_PUBLISHED, EventType.COMPETITION_UNPUBLISHED) -> {
                    val status = getPayloadAs(event.payload, CompetitionStatus::class.java)
                    if (status != null) {
                        state?.withStatus(status) to listOf(event)
                    } else {
                        state to emptyList()
                    }
                }
                else -> {
                    state to listOf(event)
                }
            }
            ns.first?.let {
                competitionStateCrudRepository.save(it)
            } to ns.second
        } catch (e: Exception) {
            state to listOf(createErrorEvent(e.localizedMessage))
        }
    }

    override fun process(command: Command, state: CompetitionState?): List<EventHolder> {
        fun executeCommand(command: Command, state: CompetitionState?): EventHolder {
            fun createEvent(type: EventType, payload: Any?) = EventHolder(command.correlationId, command.competitionId, command.categoryId
                    ?: "null", command.matId, type, mapper.writeValueAsBytes(payload))

            fun createErrorEvent(error: String) = EventHolder(command.correlationId, command.competitionId, command.categoryId
                    ?: "null", command.matId, EventType.ERROR_EVENT, mapper.writeValueAsBytes(mapOf("error" to error)))

            return when (command.type) {
                CommandType.CHECK_DASHBOARD_OBSOLETE -> {
                    if (state == null) {
                        createEvent(EventType.COMPETITION_DELETED, emptyMap<Any, Any>())
                    } else {
                        createEvent(EventType.DUMMY, emptyMap<Any, Any>())
                    }
                }
                CommandType.CREATE_COMPETITION_COMMAND -> {
                    val payload = command.payload!!
                    val tmpProps = CompetitionState(command.competitionId,
                            CompetitionProperties(command.competitionId, payload["competitionName"].toString(), payload["creatorId"].toString()))
                    val newProperties = tmpProps.properties.applyProperties(payload)
                    createEvent(EventType.COMPETITION_CREATED, mapOf("properties" to newProperties))
                }
                CommandType.CHECK_CATEGORY_OBSOLETE -> {
                    if (state == null) {
                        createEvent(EventType.CATEGORY_DELETED, command.payload ?: emptyMap<Any, Any>())
                    } else {
                        createEvent(EventType.DUMMY, emptyMap<Any, Any>())
                    }
                }
                CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> {
                    createEvent(EventType.INTERNAL_COMPETITOR_CATEGORY_CHANGED, command.payload!!)
                }
                CommandType.DROP_ALL_BRACKETS_COMMAND -> {
                    if (state?.properties?.bracketsPublished != true) {
                        val categories = stateQueryService.getCategories(command.competitionId).map { it.categoryId }
                        val newPayload = (command.payload ?: emptyMap()) + ("categories" to categories)
                        createEvent(EventType.DROP_ALL_BRACKETS_SAGA_STARTED, newPayload)
                    } else {
                        createErrorEvent("Cannot drop brackets, it is already published.")
                    }
                }
                CommandType.DROP_SCHEDULE_COMMAND -> {
                    if (state?.properties?.schedulePublished != true) {
                        createEvent(EventType.SCHEDULE_DROPPED, command.payload
                                ?: emptyMap<Any, Any>())
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
                    createEvent(EventType.COMPETITION_DELETED, mapOf("categories" to categories.map { it.categoryId }))
                }
                else -> {
                    createEvent(EventType.ERROR_EVENT, mapOf("error" to "Unknown or invalid command ${command.type}, properties: $state"))
                }
            }
        }
        return listOf(executeCommand(command, state))
    }

}