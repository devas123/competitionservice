package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.*
import compman.compsrv.jpa.es.commands.Command
import compman.compsrv.jpa.es.events.EventHolder
import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.jpa.schedule.ScheduleProperties
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.dto.schedule.ScheduleDTO
import compman.compsrv.model.dto.schedule.SchedulePropertiesDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.*
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

@Component
class CompetitionStateService(private val scheduleService: ScheduleService,
                              private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                              private val categoryCrudRepository: CategoryCrudRepository,
                              private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                              private val eventCrudRepository: EventCrudRepository,
                              private val scheduleCrudRepository: ScheduleCrudRepository,
                              private val bracketsCrudRepository: BracketsCrudRepository,
                              private val commandCrudRepository: CommandCrudRepository,
                              private val registrationGroupCrudRepository: RegistrationGroupCrudRepository,
                              private val registrationPeriodCrudRepository: RegistrationPeriodCrudRepository,
                              private val registrationInfoCrudRepository: RegistrationInfoCrudRepository,
                              private val transactionTemplate: TransactionTemplate,
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

    private fun <T> getPayloadAs(payload: String?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.readValue(payload, clazz)
        }
        return null
    }

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
                EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED -> transactionTemplate.execute {
                    val payload = getPayloadAs(event.payload, RegistrationGroupCategoriesAssignedPayload::class.java)
                    if (payload != null) {
                        val group = registrationGroupCrudRepository.findByIdOrNull(payload.groupId)
                        if (group != null) {
                            group.categories = payload.categories
                            listOf(event.setPayload(mapper.writeValueAsString(RegistrationGroupCategoriesAssignedPayload(payload.periodId, payload.groupId,
                                    registrationGroupCrudRepository.save(group).categories))))
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                }
                EventType.REGISTRATION_GROUP_ADDED -> transactionTemplate.execute {
                    val payload = getPayloadAs(event.payload, RegistrationGroupAddedPayload::class.java)!!
                    val regPeriod = registrationPeriodCrudRepository.findByIdOrNull(payload.periodId)
                    if (regPeriod != null) {
                        val group = RegistrationGroup.fromDTO(payload.group)
                        group.registrationPeriod = regPeriod
                        regPeriod.registrationGroups.add(group)
                        registrationPeriodCrudRepository.save(regPeriod)
                    }
                    val savedGroup = registrationGroupCrudRepository.findByIdOrNull(payload.group.id)
                    savedGroup?.let {
                        listOf(event.setPayload(mapper.writeValueAsString(RegistrationGroupAddedPayload(payload.periodId,
                                it.toDTO()))))
                    } ?: listOf(createErrorEvent("Could not save the group in the repository... strange..."))
                }
                EventType.REGISTRATION_GROUP_DELETED -> {
                    val payload = getPayloadAs(event.payload, RegistrationGroupDeletedPayload::class.java)!!
                    registrationGroupCrudRepository.deleteById(payload.groupId)
                    listOf(event)
                }
                EventType.REGISTRATION_PERIOD_ADDED -> transactionTemplate.execute {
                    val payload = getPayloadAs(event.payload, RegistrationPeriodAddedPayload::class.java)!!
                    val info = registrationInfoCrudRepository.findByIdOrNull(event.competitionId)
                    if (info != null) {
                        val period = RegistrationPeriod.fromDTO(payload.period)
                        period.registrationInfo = info
                        info.registrationPeriods.add(period)
                        registrationInfoCrudRepository.save(info)
                    }
                    val period = registrationPeriodCrudRepository.findByIdOrNull(payload.period.id)
                    period?.let {
                        listOf(event.setPayload(mapper.writeValueAsString(RegistrationPeriodAddedPayload(it.toDTO()))))
                    } ?: listOf(createErrorEvent("Could not save the period in the repository... strange..."))
                }
                EventType.REGISTRATION_PERIOD_DELETED -> {
                    val payload = getPayloadAs(event.payload, String::class.java)
                    registrationPeriodCrudRepository.deleteById(payload!!)
                    listOf(event)
                }
                EventType.COMPETITION_DELETED -> transactionTemplate.execute {
                    competitionStateCrudRepository.findById(event.competitionId).map { it.withStatus(CompetitionStatus.DELETED) }.map {
                        competitionStateCrudRepository.save(it)
                        listOf(event)
                    }.orElse(emptyList())
                }
                EventType.COMPETITION_CREATED -> {
                    val payload = getPayloadAs(event.payload, CompetitionCreatedPayload::class.java)
                    payload?.properties?.let { props ->
                        val state = CompetitionState(props.id, CompetitionProperties.fromDTO(props))
                        val newState = competitionStateCrudRepository.save(state)
                        val newPayload = CompetitionCreatedPayload(newState.properties!!.toDTO())
                        val createdEvent = EventDTO(event.id, event.correlationId, event.competitionId,
                                event.categoryId, event.matId, event.type, mapper.writeValueAsString(newPayload), event.metadata)
                        listOf(createdEvent)
                    } ?: listOf(createErrorEvent("Properties are missing."))
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
                    comp.properties = comp.properties?.applyProperties(payload?.properties)
                    competitionStateCrudRepository.save(comp)
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
                EventType.ERROR_EVENT -> {
                    listOf(event)
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
            log.error("Error while applying event.", e)
            listOf(createErrorEvent(e.localizedMessage))
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
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
                CommandType.ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, AssignRegistrationGroupCategoriesPayload::class.java)
                    if (registrationPeriodCrudRepository.existsById(payload.periodId)) {
                        if (registrationGroupCrudRepository.existsById(payload.groupId)) {
                            createEvent(EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED, RegistrationGroupCategoriesAssignedPayload(payload.periodId, payload.groupId, payload.categories))
                        } else {
                            createErrorEvent("Unknown group id: ${payload.groupId}")
                        }
                    } else {
                        createErrorEvent("Unknown period id: ${payload.periodId}")
                    }
                }
                CommandType.DELETE_REGISTRATION_PERIOD_COMMAND -> {
                    createEvent(EventType.REGISTRATION_PERIOD_DELETED, command.payload["periodId"])
                }
                CommandType.DELETE_REGISTRATION_GROUP_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, DeleteRegistrationGroupPayload::class.java)
                    createEvent(EventType.REGISTRATION_GROUP_DELETED, RegistrationGroupDeletedPayload(payload.periodId, payload.groupId))
                }
                CommandType.ADD_REGISTRATION_GROUP_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, AddRegistrationGroupPayload::class.java)
                    if (!payload.periodId.isNullOrBlank() && !payload.group.id.isNullOrBlank()) {
                        val period = registrationPeriodCrudRepository.findById(payload.periodId)
                        if (!payload.group.displayName.isNullOrBlank()) {
                            period.map { p ->
                                if (!p.registrationGroups.any { it.id == payload.group.id }) {
                                    createEvent(EventType.REGISTRATION_GROUP_ADDED, RegistrationGroupAddedPayload(payload.periodId, payload.group))
                                } else {
                                    createErrorEvent("Group with id ${payload.group.id} already exists")
                                }
                            }.orElse(createErrorEvent("Cannot find period with ID: ${payload.periodId}"))
                        } else {
                            createErrorEvent("Group name is not specified.")
                        }
                    } else {
                        createErrorEvent("Period Id is not specified")
                    }
                }
                CommandType.ADD_REGISTRATION_PERIOD_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, AddRegistrationPeriodPayload::class.java)
                    if (payload.period != null && !command.competitionId.isNullOrBlank()) {
                        val regInfo = registrationInfoCrudRepository.findById(command.competitionId)
                        regInfo.map { info ->
                            if (!info.registrationPeriods.any { it.id == payload.period.id }) {
                                createEvent(EventType.REGISTRATION_PERIOD_ADDED, RegistrationPeriodAddedPayload(payload.period))
                            } else {
                                createErrorEvent("Period with id ${payload.period.id} already exists.")
                            }
                        }.orElseGet {
                            try {
                                registrationInfoCrudRepository.save(RegistrationInfo(command.competitionId, false, mutableListOf()))
                                createEvent(EventType.REGISTRATION_PERIOD_ADDED, RegistrationPeriodAddedPayload(payload.period))
                            } catch (e: Throwable) {
                                log.error("Exception.", e)
                                createErrorEvent("Registration info is missing for the competition ${command.competitionId}, exception when adding: $e")
                            }
                        }
                    } else {
                        createErrorEvent("Period is not specified or competition id is missing.")
                    }
                }
                CommandType.CREATE_COMPETITION_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, CreateCompetitionPayload::class.java)
                    val newProperties = payload?.properties
                    if (newProperties != null) {
                        if (newProperties.startDate == null) {
                            newProperties.startDate = Instant.now()
                        }
                        if (newProperties.endDate == null) {
                            newProperties.endDate = Instant.now()
                        }
                        if (newProperties.timeZone.isNullOrBlank() || newProperties.timeZone == "null") {
                            newProperties.timeZone = ZoneId.systemDefault().id
                        }
                        createEvent(EventType.COMPETITION_CREATED, CompetitionCreatedPayload(newProperties))
                    } else {
                        createErrorEvent("Cannot create competition, no properties provided")
                    }
                }
                CommandType.DROP_ALL_BRACKETS_COMMAND -> {
                    val state = competitionStateCrudRepository.getOne(command.competitionId)
                    if (state.properties?.bracketsPublished != true) {
                        createEvent(EventType.ALL_BRACKETS_DROPPED, command.payload)
                    } else {
                        createErrorEvent("Cannot drop brackets, they are already published.")
                    }
                }
                CommandType.DROP_SCHEDULE_COMMAND -> {
                    val state = competitionStateCrudRepository.getOne(command.competitionId)
                    if (state.properties?.schedulePublished != true) {
                        createEvent(EventType.SCHEDULE_DROPPED, command.payload)
                    } else {
                        createErrorEvent("Cannot drop schedule, it is already published.")
                    }
                }
                CommandType.GENERATE_SCHEDULE_COMMAND -> {
                    val payload = command.payload!!
                    val scheduleProperties = mapper.convertValue(payload, SchedulePropertiesDTO::class.java)
                    val compProps = competitionPropertiesCrudRepository.findByIdOrNull(command.competitionId)
                    val schedule = scheduleService.generateSchedule(ScheduleProperties.fromDTO(scheduleProperties), getAllBrackets(scheduleProperties.competitionId), getFightDurations(scheduleProperties), compProps?.timeZone
                            ?: ZoneId.systemDefault().id)
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