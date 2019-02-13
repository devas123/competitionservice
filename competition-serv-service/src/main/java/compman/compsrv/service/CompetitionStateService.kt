package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.jpa.es.commands.Command
import compman.compsrv.jpa.es.events.EventHolder
import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.jpa.schedule.ScheduleProperties
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.dto.schedule.SchedulePropertiesDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Component
class CompetitionStateService(private val scheduleService: ScheduleService,
                              private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                              private val eventCrudRepository: EventCrudRepository,
                              private val scheduleCrudRepository: ScheduleCrudRepository,
                              private val bracketsCrudRepository: BracketsCrudRepository,
                              private val commandCrudRepository: CommandCrudRepository,
                              private val mapper: ObjectMapper) : ICommandProcessingService<CommandDTO, EventDTO> {

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionStateService::class.java)
    }

    private fun getAllBrackets(competitionId: String): List<BracketDescriptor> {
        val competitionState = competitionStateCrudRepository.findById(competitionId)
        return competitionState.map { t: CompetitionState? ->
            t?.categories?.mapNotNull { it.brackets }
        }?.orElse(emptyList()) ?: emptyList()
    }

    private fun getFightDurations(scheduleProperties: SchedulePropertiesDTO): Map<String, BigDecimal> {
        val categories = scheduleProperties.periodPropertiesList.flatMap { it.categories.toList() }
        return categories.map { it.id to it.fightDuration }.toMap()
    }

    private fun <T> getPayloadAs(payload: Any?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.convertValue(payload, clazz)
        }
        return null
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun apply(event: EventDTO): List<EventDTO> {
        fun createErrorEvent(error: String) =
                EventDTO()
                        .setCategoryId(event.categoryId)
                        .setCorrelationId(event.correlationId ?: "")
                        .setCompetitionId(event.competitionId)
                        .setMatId(event.matId)
                        .setType(EventType.ERROR_EVENT)
                        .setPayload(mapper.writeValueAsString(ErrorEventPayload(error, null)))
        return try {
            val ns = when (event.type) {
                EventType.COMPETITION_DELETED -> {
                    competitionStateCrudRepository.findById(event.competitionId).map { it.withStatus(CompetitionStatus.DELETED) }.map {
                        competitionStateCrudRepository.save(it)
                        listOf(event)
                    }.orElse(emptyList())
                }
                EventType.COMPETITION_CREATED -> {
                    val newstate = getPayloadAs(event.payload, CompetitionState::class.java)
                    newstate?.let {
                        competitionStateCrudRepository.save(newstate)
                        listOf(event)
                    }
                }
                EventType.DUMMY -> {
                    emptyList()
                }
                EventType.SCHEDULE_DROPPED -> {
                    scheduleCrudRepository.deleteById(event.competitionId)
                    listOf(event)
                }
                EventType.ALL_BRACKETS_DROPPED -> {
                    bracketsCrudRepository.deleteByCompetitionId(event.competitionId)
                    listOf(event)
                }
                EventType.SCHEDULE_GENERATED -> {
                    val schedule = getPayloadAs(event.payload, ScheduleDTO::class.java)
                    schedule?.let {
                        scheduleCrudRepository.save(Schedule.fromDTO(it))
                        listOf(event)
                    }
                }
                EventType.COMPETITION_PROPERTIES_UPDATED -> {
                    val payload = getPayloadAs(event.payload, CompetitionPropertiesUpdatedPayload::class.java)
                    val comp = competitionStateCrudRepository.getOne(event.competitionId)
                    competitionStateCrudRepository.save(comp.copy(properties = comp.properties.applyProperties(payload?.properties)))
                    listOf(event)
                }
                in listOf(EventType.COMPETITION_STARTED, EventType.COMPETITION_STOPPED, EventType.COMPETITION_PUBLISHED, EventType.COMPETITION_UNPUBLISHED) -> {
                    val status = getPayloadAs(event.payload, CompetitionStatus::class.java)
                    if (status != null) {
                        competitionStateCrudRepository.findById(event.competitionId).map {
                            competitionStateCrudRepository.save(it.withStatus(status))
                            listOf(event)
                        }.orElse(emptyList())
                    } else {
                        emptyList()
                    }
                }
                else -> {
                    log.warn("Skipping unknown event: $event")
                    emptyList()
                }
            }
            ns?.let {
                eventCrudRepository.saveAll(it.map { eventDTO -> EventHolder.fromDTO(eventDTO) })
            }
            ns ?: emptyList()
        } catch (e: Exception) {
            listOf(createErrorEvent(e.localizedMessage))
        }
    }

    override fun process(command: CommandDTO): List<EventDTO> {
        fun executeCommand(command: CommandDTO): EventDTO {
            fun createEvent(type: EventType, payload: Any?) =
                    EventDTO()
                            .setCategoryId(command.categoryId)
                            .setCorrelationId(command.correlationId)
                            .setCompetitionId(command.competitionId)
                            .setMatId(command.matId)
                            .setType(type)
                            .setPayload(mapper.writeValueAsString(payload))

            fun createErrorEvent(error: String) = EventDTO()
                    .setCategoryId(command.categoryId)
                    .setCorrelationId(command.correlationId)
                    .setCompetitionId(command.competitionId)
                    .setMatId(command.matId)
                    .setType(EventType.ERROR_EVENT)
                    .setPayload(mapper.writeValueAsString(ErrorEventPayload(error, command.correlationId)))

            return when (command.type) {
                CommandType.CREATE_COMPETITION_COMMAND -> {
                    val payload = getPayloadAs(command.payload, CreateCompetitionPayload::class.java)
                    val newProperties = payload?.properties
                    if (newProperties != null) {
                        createEvent(EventType.COMPETITION_CREATED, CompetitionCreatedPayload(newProperties))
                    } else {
                        createErrorEvent("Cannot create competition, no properties provided")
                    }
                }
                CommandType.DROP_ALL_BRACKETS_COMMAND -> {
                    val state = competitionStateCrudRepository.getOne(command.competitionId)
                    if (!state.properties.bracketsPublished) {
                        createEvent(EventType.ALL_BRACKETS_DROPPED, command.payload)
                    } else {
                        createErrorEvent("Cannot drop brackets, they are already published.")
                    }
                }
                CommandType.DROP_SCHEDULE_COMMAND -> {
                    val state = competitionStateCrudRepository.getOne(command.competitionId)
                    if (!state.properties.schedulePublished) {
                        createEvent(EventType.SCHEDULE_DROPPED, command.payload)
                    } else {
                        createErrorEvent("Cannot drop schedule, it is already published.")
                    }
                }
                CommandType.GENERATE_SCHEDULE_COMMAND -> {
                    val payload = command.payload!!
                    val scheduleProperties = mapper.convertValue(payload, SchedulePropertiesDTO::class.java)
                    val schedule = scheduleService.generateSchedule(ScheduleProperties.fromDTO(scheduleProperties), getAllBrackets(scheduleProperties.competitionId), getFightDurations(scheduleProperties))
                    createEvent(EventType.SCHEDULE_GENERATED, ScheduleGeneratedPayload(schedule.toDTO()))
                }
                CommandType.UPDATE_COMPETITION_PROPERTIES_COMMAND -> {
                    createEvent(EventType.COMPETITION_PROPERTIES_UPDATED, command.payload!!)
                }
                CommandType.START_COMPETITION_COMMAND -> {
                    createEvent(EventType.COMPETITION_STARTED, CompetitionStatusUpdatedPayload(CompetitionStatus.STARTED))
                }
                CommandType.STOP_COMPETITION_COMMAND -> {
                    createEvent(EventType.COMPETITION_STOPPED, CompetitionStatusUpdatedPayload(CompetitionStatus.STOPPED))
                }
                CommandType.PUBLISH_COMPETITION_COMMAND -> {
                    createEvent(EventType.COMPETITION_PUBLISHED, CompetitionStatusUpdatedPayload(CompetitionStatus.PUBLISHED))
                }
                CommandType.UNPUBLISH_COMPETITION_COMMAND -> {
                    createEvent(EventType.COMPETITION_UNPUBLISHED, CompetitionStatusUpdatedPayload(CompetitionStatus.UNPUBLISHED))
                }
                CommandType.DELETE_COMPETITION_COMMAND -> {
                    createEvent(EventType.COMPETITION_DELETED, null)
                }
                else -> {
                    createEvent(EventType.ERROR_EVENT, ErrorEventPayload("Unknown or invalid command ${command.type}", command.correlationId))
                }
            }
        }
        log.info("Executing command: $command")
        commandCrudRepository.save(Command.fromDTO(command))
        return listOf(executeCommand(command))
    }

}