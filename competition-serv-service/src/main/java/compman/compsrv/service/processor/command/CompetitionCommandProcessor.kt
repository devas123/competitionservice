package compman.compsrv.service.processor.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.dashboard.CompetitionDashboardState
import compman.compsrv.jpa.competition.RegistrationInfo
import compman.compsrv.jpa.dashboard.DashboardPeriod
import compman.compsrv.jpa.dashboard.MatDescription
import compman.compsrv.mapping.toDTO
import compman.compsrv.mapping.toEntity
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
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.createEvent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.util.*

@Lazy
@Component
class CompetitionCommandProcessor(private val scheduleService: ScheduleService,
                                  private val clusterSession: ClusterSession,
                                  private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                                  private val competitorCrudRepository: CompetitorCrudRepository,
                                  private val categoryCrudRepository: CategoryStateCrudRepository,
                                  private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                                  private val bracketsCrudRepository: BracketsCrudRepository,
                                  private val registrationGroupCrudRepository: RegistrationGroupCrudRepository,
                                  private val registrationPeriodCrudRepository: RegistrationPeriodCrudRepository,
                                  private val registrationInfoCrudRepository: RegistrationInfoCrudRepository,
                                  private val dashboardStateCrudRepository: DashboardStateCrudRepository,
                                  private val mapper: ObjectMapper) : ICommandProcessor {
    override fun affectedCommands(): Set<CommandType> {
        return setOf(CommandType.ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND,
                CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND,
                CommandType.DELETE_REGISTRATION_PERIOD_COMMAND,
                CommandType.DELETE_REGISTRATION_GROUP_COMMAND,
                CommandType.CREATE_REGISTRATION_GROUP_COMMAND,
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
                CommandType.DELETE_COMPETITION_COMMAND,
                CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND,
                CommandType.UPDATE_REGISTRATION_INFO_COMMAND)
    }

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionCommandProcessor::class.java)
    }

    private fun getAllBrackets(competitionId: String): List<BracketDescriptor> {
        return bracketsCrudRepository.findByCompetitionId(competitionId) ?: emptyList()
    }


    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    override fun executeCommand(command: CommandDTO): List<EventDTO> {
        fun execute(command: CommandDTO): List<EventDTO> {
            fun createEvent(type: EventType, payload: Any?) = mapper.createEvent(command, type, payload)

            fun createErrorEvent(error: String) = mapper.createErrorEvent(command, error)

            if (command.competitionId.isNullOrBlank()) {
                log.error("Competition id is empty, command $command")
                return listOf(createErrorEvent("Competition ID is empty."))
            }

            return when (command.type) {
                CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND -> {
                    clusterSession.createProcessingInfoEvents(command.correlationId, setOf(command.competitionId)).toList()
                }
                CommandType.UPDATE_REGISTRATION_INFO_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, UpdateRegistrationInfoPayload::class.java)

                    if (!payload?.registrationInfo?.id.isNullOrBlank() && registrationInfoCrudRepository.existsById(payload.registrationInfo.id)) {
                        listOf(createEvent(EventType.REGISTRATION_INFO_UPDATED, RegistrationInfoUpdatedPayload(payload?.registrationInfo)))
                    } else {
                        listOf(createErrorEvent("Registration info not provided, or does not exist for id ${payload?.registrationInfo?.id}"))
                    }
                }
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
                                val matIds = if (!fightsByMats.isNullOrEmpty()) {
                                    fightsByMats.groupBy { it.id }.keys.filterNotNull().toMutableSet()
                                } else {
                                    (0..period.numberOfMats).map { number -> IDGenerator.hashString("${command.competitionId}/${period.id}/$number") }.toMutableSet()
                                }
                                val id = if (!period.id.isNullOrBlank()) {
                                    period.id!!
                                } else {
                                    IDGenerator.hashString("${command.competitionId}/dashboard/period/${period.name}")
                                }
                                DashboardPeriod(id, period.name, mutableListOf(), period.startTime, false).apply {
                                    mats = matIds
                                            .mapIndexed { index, s -> MatDescription(s, "Mat ${index + 1}", this) }
                                            .toMutableList()
                                }
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
                    if (registrationGroupCrudRepository.existsById(payload.groupId)
                            && registrationPeriodCrudRepository.existsById(payload.periodId)) {
                        listOf(createEvent(EventType.REGISTRATION_GROUP_DELETED, RegistrationGroupDeletedPayload(payload.periodId, payload.groupId)))
                    } else {
                        emptyList()
                    }
                }
                CommandType.CREATE_REGISTRATION_GROUP_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, AddRegistrationGroupPayload::class.java)
                    if (!payload.periodId.isNullOrBlank()) {
                        val period = registrationPeriodCrudRepository.findById(payload.periodId)
                        if (!payload.group?.displayName.isNullOrBlank() && !payload.group?.registrationInfoId.isNullOrBlank()) {
                            val groupId = IDGenerator.hashString("${payload.periodId}/${payload.group.displayName}")
                            val regInfoId = payload.group.registrationInfoId ?: command.competitionId
                            val defaultGroup = Optional.ofNullable(payload.group.defaultGroup).flatMap {
                                if (it) {
                                    registrationGroupCrudRepository.findDefaultGroupByRegistrationInfoId(regInfoId)
                                } else {
                                    Optional.empty()
                                }
                            }

                            if (defaultGroup.isPresent) {
                                listOf(createErrorEvent("There is already a default group for competition ${command.competitionId}: ${defaultGroup.map { gr -> gr.displayName }.orElse("<Unknown>")}"))
                            } else {
                                period.map { p ->
                                    if (p.registrationGroups?.any { it.id == groupId } != true) {
                                        listOf(createEvent(EventType.REGISTRATION_GROUP_CREATED, RegistrationGroupAddedPayload(payload.periodId, payload.group.setId(groupId).setRegistrationInfoId(regInfoId))))
                                    } else {
                                        listOf(createErrorEvent("Group with id $groupId already exists"))
                                    }
                                }.orElse(listOf(createErrorEvent("Cannot find period with ID: ${payload.periodId}")))
                            }

                        } else {
                            listOf(createErrorEvent("Group name is not specified ${payload.group?.displayName}."))
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
                                registrationInfoCrudRepository.save(RegistrationInfo(command.competitionId, false, mutableSetOf()))
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
                    val categories = scheduleProperties.periodPropertiesList?.flatMap {
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
                            val categoryIds = categories?.map { it.id } ?: emptyList()
                            val competitors = competitorCrudRepository.findByCompetitionIdAndCategoriesContaining(command.competitionId, categoryIds, Pageable.unpaged()).content

                            val schedule = scheduleService.generateSchedule(scheduleProperties.toEntity {id -> competitors.firstOrNull { competitor -> competitor.id == id }}, getAllBrackets(scheduleProperties.competitionId), compProps.timeZone)
                            val newFights = schedule.periods?.flatMap { period -> period.fightsByMats?.flatMap { it.fights.map { f -> f.toDTO(it.id!!).setPeriodId(period.id) } } ?: emptyList() }?.toTypedArray()
                            val fightStartTimeUpdatedPayload = FightStartTimeUpdatedPayload().setNewFights(newFights)

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
        val events = execute(command)
        return events.mapIndexed { _, eventDTO -> eventDTO.setId(IDGenerator.uid()) }
    }

}