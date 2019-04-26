package compman.compsrv.service.processor.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.CompetitionDashboardState
import compman.compsrv.jpa.competition.RegistrationInfo
import compman.compsrv.jpa.es.commands.Command
import compman.compsrv.jpa.schedule.DashboardPeriod
import compman.compsrv.jpa.schedule.ScheduleProperties
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.dto.schedule.SchedulePropertiesDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.*
import compman.compsrv.service.ScheduleService
import compman.compsrv.util.IDGenerator
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.*

@Component
class CompetitionCommandProcessor(private val scheduleService: ScheduleService,
                                  private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                                  private val categoryCrudRepository: CategoryStateCrudRepository,
                                  private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                                  private val bracketsCrudRepository: BracketsCrudRepository,
                                  private val commandCrudRepository: CommandCrudRepository,
                                  private val registrationGroupCrudRepository: RegistrationGroupCrudRepository,
                                  private val registrationPeriodCrudRepository: RegistrationPeriodCrudRepository,
                                  private val registrationInfoCrudRepository: RegistrationInfoCrudRepository,
                                  private val dashboardStateCrudRepository: DashboardStateCrudRepository,
                                  private val mapper: ObjectMapper) : ICommandProcessor {
    override fun affectedCommands(): Set<CommandType> {
        return setOf(CommandType.ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND,
                CommandType.DELETE_REGISTRATION_PERIOD_COMMAND,
                CommandType.DELETE_REGISTRATION_GROUP_COMMAND,
                CommandType.ADD_REGISTRATION_GROUP_COMMAND,
                CommandType.ADD_REGISTRATION_PERIOD_COMMAND,
                CommandType.CREATE_COMPETITION_COMMAND,
                CommandType.CREATE_DASHBOARD_COMMAND,
                CommandType.DELETE_DASHBOARD_COMMAND,
                CommandType.DROP_ALL_BRACKETS_COMMAND,
                CommandType.DROP_SCHEDULE_COMMAND,
                CommandType.GENERATE_SCHEDULE_COMMAND,
                CommandType.UPDATE_COMPETITION_PROPERTIES_COMMAND,
                CommandType.START_COMPETITION_COMMAND,
                CommandType.STOP_COMPETITION_COMMAND,
                CommandType.PUBLISH_COMPETITION_COMMAND,
                CommandType.UNPUBLISH_COMPETITION_COMMAND,
                CommandType.DELETE_COMPETITION_COMMAND)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionCommandProcessor::class.java)
    }

    private fun getAllBrackets(competitionId: String): List<BracketDescriptor> {
        return bracketsCrudRepository.findByCompetitionId(competitionId) ?: emptyList()
    }

    private fun getFightDurations(scheduleProperties: SchedulePropertiesDTO): Map<String, BigDecimal> {
        val categories = scheduleProperties.periodPropertiesList.flatMap { it.categories.toList() }
        return categories.map { it.id to it.fightDuration }.toMap()
    }


    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    override fun executeCommand(command: CommandDTO): List<EventDTO> {
        fun execute(command: CommandDTO): List<EventDTO> {
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

            if (command.competitionId.isNullOrBlank()) {
                log.error("Competition id is empty, command $command")
                return listOf(createErrorEvent("Competition ID is empty."))
            }

            return when (command.type) {
                CommandType.ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, AssignRegistrationGroupCategoriesPayload::class.java)
                    if (registrationPeriodCrudRepository.existsById(payload.periodId)) {
                        if (registrationGroupCrudRepository.existsById(payload.groupId)) {
                            listOf(createEvent(EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED, RegistrationGroupCategoriesAssignedPayload(payload.periodId, payload.groupId, payload.categories)))
                        } else {
                            listOf(createErrorEvent("Unknown group id: ${payload.groupId}"))
                        }
                    } else {
                        listOf(createErrorEvent("Unknown period id: ${payload.periodId}"))
                    }
                }
                CommandType.DELETE_REGISTRATION_PERIOD_COMMAND -> {
                    listOf(createEvent(EventType.REGISTRATION_PERIOD_DELETED, command.payload["periodId"]))
                }
                CommandType.DELETE_DASHBOARD_COMMAND -> {
                    listOf(createEvent(EventType.DASHBOARD_DELETED, command.competitionId))
                }
                CommandType.CREATE_DASHBOARD_COMMAND -> {
                    val dashboardState = dashboardStateCrudRepository.findByIdOrNull(command.competitionId)
                    if (dashboardState == null || dashboardState.periods.isNullOrEmpty()) {
                        val schedule = competitionStateCrudRepository.findByIdOrNull(command.competitionId)?.schedule
                        val periods = schedule?.periods
                        if (!periods.isNullOrEmpty()) {
                            val dashbPeriods = periods.map { period ->
                                val fightsByMats = period.fightsByMats
                                val mats = if (!fightsByMats.isNullOrEmpty()) {
                                    fightsByMats.groupBy { it.id }.keys.filterNotNull().toTypedArray()
                                } else {
                                    (0..period.numberOfMats).map { number -> IDGenerator.hashString("${command.competitionId}/${period.id}/$number") }.toTypedArray()
                                }
                                val id = if (!period.id.isNullOrBlank()) {
                                    period.id!!
                                } else {
                                    IDGenerator.hashString("${command.competitionId}/dashboard/period/${period.name}")
                                }
                                DashboardPeriod(id, period.name, mats, period.startTime, false)
                            }.toSet()
                            val state = CompetitionDashboardState(command.competitionId, dashbPeriods)
                            listOf(createEvent(EventType.DASHBOARD_CREATED, DashboardCreatedPayload(state.toDTO())))
                        } else {
                            listOf(createErrorEvent("Schedule not generated for competition ID: ${command.competitionId}"))
                        }
                    } else {
                        listOf(createErrorEvent("Dashboard already exists for competition ID: ${command.competitionId}"))
                    }
                }
                CommandType.DELETE_REGISTRATION_GROUP_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, DeleteRegistrationGroupPayload::class.java)
                    listOf(createEvent(EventType.REGISTRATION_GROUP_DELETED, RegistrationGroupDeletedPayload(payload.periodId, payload.groupId)))
                }
                CommandType.ADD_REGISTRATION_GROUP_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, AddRegistrationGroupPayload::class.java)
                    if (!payload.periodId.isNullOrBlank()) {
                        val period = registrationPeriodCrudRepository.findById(payload.periodId)
                        if (!payload.group.displayName.isNullOrBlank()) {
                            val groupId = IDGenerator.hashString("${payload.periodId}/${payload.group.displayName}")
                            period.map { p ->
                                if (!p.registrationGroups.any { it.id == groupId }) {
                                    listOf(createEvent(EventType.REGISTRATION_GROUP_ADDED, RegistrationGroupAddedPayload(payload.periodId, payload.group.setId(groupId))))
                                } else {
                                    listOf(createErrorEvent("Group with id $groupId already exists"))
                                }
                            }.orElse(listOf(createErrorEvent("Cannot find period with ID: ${payload.periodId}")))
                        } else {
                            listOf(createErrorEvent("Group name is not specified."))
                        }
                    } else {
                        listOf(createErrorEvent("Period Id is not specified"))
                    }
                }
                CommandType.ADD_REGISTRATION_PERIOD_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, AddRegistrationPeriodPayload::class.java)
                    if (payload.period != null && !command.competitionId.isNullOrBlank()) {
                        val regInfo = registrationInfoCrudRepository.findById(command.competitionId)
                        val periodId = IDGenerator.hashString("${command.competitionId}/${payload.period.name}")
                        regInfo.map { info ->
                            if (!info.registrationPeriods.any { it.id == periodId }) {
                                listOf(createEvent(EventType.REGISTRATION_PERIOD_ADDED, RegistrationPeriodAddedPayload(payload.period.setId(periodId))))
                            } else {
                                listOf(createErrorEvent("Period with id ${payload.period.id} already exists."))
                            }
                        }.orElseGet {
                            try {
                                registrationInfoCrudRepository.save(RegistrationInfo(command.competitionId, false, mutableListOf()))
                                listOf(createEvent(EventType.REGISTRATION_PERIOD_ADDED, RegistrationPeriodAddedPayload(payload.period.setId(periodId))))
                            } catch (e: Throwable) {
                                log.error("Exception.", e)
                                listOf(createErrorEvent("Registration info is missing for the competition ${command.competitionId}, exception when adding: $e"))
                            }
                        }
                    } else {
                        listOf(createErrorEvent("Period is not specified or competition id is missing."))
                    }
                }
                CommandType.CREATE_COMPETITION_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, CreateCompetitionPayload::class.java)
                    val newProperties = payload?.properties
                    if (newProperties != null) {
                        if (!newProperties.competitionName.isNullOrBlank()) {
                            if (newProperties.startDate == null) {
                                newProperties.startDate = Instant.now()
                            }
                            if (newProperties.endDate == null) {
                                newProperties.endDate = Instant.now()
                            }
                            if (newProperties.timeZone.isNullOrBlank() || newProperties.timeZone == "null") {
                                newProperties.timeZone = ZoneId.systemDefault().id
                            }
                            if (!competitionStateCrudRepository.existsById(command.competitionId)) {
                                listOf(createEvent(EventType.COMPETITION_CREATED, CompetitionCreatedPayload(newProperties.setId(command.competitionId))))
                            } else {
                                listOf(createErrorEvent("Competition with name '${newProperties.competitionName}' already exists."))
                            }
                        } else {
                            listOf(createErrorEvent("Competition name is empty"))
                        }
                    } else {
                        listOf(createErrorEvent("Cannot create competition, no properties provided"))
                    }
                }
                CommandType.DROP_ALL_BRACKETS_COMMAND -> {
                    val state = competitionStateCrudRepository.getOne(command.competitionId)
                    if (state.properties?.bracketsPublished != true) {
                        listOf(createEvent(EventType.ALL_BRACKETS_DROPPED, command.payload))
                    } else {
                        listOf(createErrorEvent("Cannot drop brackets, they are already published."))
                    }
                }
                CommandType.DROP_SCHEDULE_COMMAND -> {
                    val state = competitionStateCrudRepository.getOne(command.competitionId)
                    if (state.properties?.schedulePublished != true) {
                        listOf(createEvent(EventType.SCHEDULE_DROPPED, command.payload))
                    } else {
                        listOf(createErrorEvent("Cannot drop schedule, it is already published."))
                    }
                }
                CommandType.GENERATE_SCHEDULE_COMMAND -> {
                    val payload = command.payload!!
                    val scheduleProperties = mapper.convertValue(payload, SchedulePropertiesDTO::class.java)
                    scheduleProperties.periodPropertiesList = scheduleProperties.periodPropertiesList.map {
                        if (it.id.isNullOrBlank()) {
                            it.setId("${command.competitionId}-${UUID.randomUUID()}")
                        } else {
                            it
                        }
                    }.toTypedArray()
                    val compProps = competitionPropertiesCrudRepository.findByIdOrNull(command.competitionId)
                    val categories = scheduleProperties?.periodPropertiesList?.flatMap {
                        it.categories?.toList() ?: emptyList()
                    }
                    val missingCategories = categories?.fold(emptyList<String>(), { acc, cat ->
                        if (categoryCrudRepository.existsById(cat.id)) {
                            acc
                        } else {
                            acc + cat.id
                        }
                    })
                    if (compProps != null && !compProps.schedulePublished) {
                        if (missingCategories.isNullOrEmpty()) {
                            val schedule = scheduleService.generateSchedule(ScheduleProperties.fromDTO(scheduleProperties), getAllBrackets(scheduleProperties.competitionId), getFightDurations(scheduleProperties), compProps.timeZone)
                            val fightStartTimeUpdatedPayload = FightStartTimeUpdatedPayload().setNewFights(schedule.periods?.flatMap { period -> period.fightsByMats ?: emptyList() }?.flatMap { it.fights.map { f -> f.toDTO(it.id ?: "") } }?.toTypedArray())
                            listOf(createEvent(EventType.SCHEDULE_GENERATED, ScheduleGeneratedPayload(schedule.toDTO())), createEvent(EventType.FIGHTS_START_TIME_UPDATED, fightStartTimeUpdatedPayload))
                        } else {
                            listOf(createErrorEvent("Categories $missingCategories are missing"))
                        }
                    } else {
                        listOf(createErrorEvent("could not find competition with ID: ${command.competitionId}"))
                    }
                }
                CommandType.UPDATE_COMPETITION_PROPERTIES_COMMAND -> {
                    listOf(createEvent(EventType.COMPETITION_PROPERTIES_UPDATED, command.payload!!))
                }
                CommandType.START_COMPETITION_COMMAND -> {
                    listOf(createEvent(EventType.COMPETITION_STARTED, CompetitionStatusUpdatedPayload(CompetitionStatus.STARTED)))
                }
                CommandType.STOP_COMPETITION_COMMAND -> {
                    listOf(createEvent(EventType.COMPETITION_STOPPED, CompetitionStatusUpdatedPayload(CompetitionStatus.STOPPED)))
                }
                CommandType.PUBLISH_COMPETITION_COMMAND -> {
                    listOf(createEvent(EventType.COMPETITION_PUBLISHED, CompetitionStatusUpdatedPayload(CompetitionStatus.PUBLISHED)))
                }
                CommandType.UNPUBLISH_COMPETITION_COMMAND -> {
                    listOf(createEvent(EventType.COMPETITION_UNPUBLISHED, CompetitionStatusUpdatedPayload(CompetitionStatus.UNPUBLISHED)))
                }
                CommandType.DELETE_COMPETITION_COMMAND -> {
                    listOf(createEvent(EventType.COMPETITION_DELETED, null))
                }
                else -> {
                    listOf(createEvent(EventType.ERROR_EVENT, ErrorEventPayload("Unknown or invalid command ${command.type}", command.correlationId)))
                }
            }
        }
        log.info("Executing command: $command")
        commandCrudRepository.save(Command.fromDTO(command))
        return execute(command)
    }

}