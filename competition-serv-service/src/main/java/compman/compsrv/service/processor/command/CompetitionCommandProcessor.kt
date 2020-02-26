package compman.compsrv.service.processor.command

import arrow.core.Either
import arrow.core.Tuple3
import arrow.core.flatMap
import com.compmanager.compservice.jooq.tables.daos.*
import com.compmanager.compservice.jooq.tables.pojos.FightDescription
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.dto.competition.RegistrationGroupDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.JooqQueries
import compman.compsrv.service.ScheduleService
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.createEvent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.time.Instant
import java.time.ZoneId

@Lazy
@Component
class CompetitionCommandProcessor(private val scheduleService: ScheduleService,
                                  private val clusterSession: ClusterSession,
                                  private val categoryCrudRepository: CategoryDescriptorDao,
                                  private val competitionPropertiesCrudRepository: CompetitionPropertiesDao,
                                  private val stageDescriptorCrudRepository: StageDescriptorDao,
                                  private val fightDescriptionDao: FightDescriptionDao,
                                  private val registrationGroupCrudRepository: RegistrationGroupDao,
                                  private val registrationPeriodCrudRepository: RegistrationPeriodDao,
                                  private val registrationInfoCrudRepository: RegistrationInfoDao,
                                  private val jooqQueries: JooqQueries,
                                  private val mapper: ObjectMapper) : ICommandProcessor {
    override fun affectedCommands(): Set<CommandType> {
        return setOf(CommandType.ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND,
                CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND,
                CommandType.DELETE_REGISTRATION_PERIOD_COMMAND,
                CommandType.DELETE_REGISTRATION_GROUP_COMMAND,
                CommandType.ADD_REGISTRATION_GROUP_COMMAND,
                CommandType.ADD_REGISTRATION_PERIOD_COMMAND,
                CommandType.CREATE_COMPETITION_COMMAND,
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

    private fun getAllBrackets(competitionId: String): Flux<Pair<Tuple3<String, String, BracketType>, List<FightDescription>>> {
        return Flux.fromIterable(stageDescriptorCrudRepository.fetchByCompetitionId(competitionId)).map {
            Tuple3(it.id,  it.categoryId, BracketType.values()[it.bracketType]) to (fightDescriptionDao.fetchByStageId(competitionId) ?: emptyList())
        }
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
                CommandType.DELETE_REGISTRATION_GROUP_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, DeleteRegistrationGroupPayload::class.java)
                    if (registrationGroupCrudRepository.existsById(payload.groupId)
                            && registrationPeriodCrudRepository.existsById(payload.periodId)) {
                        listOf(createEvent(EventType.REGISTRATION_GROUP_DELETED, RegistrationGroupDeletedPayload(payload.periodId, payload.groupId)))
                    } else {
                        emptyList()
                    }
                }
                CommandType.ADD_REGISTRATION_GROUP_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, AddRegistrationGroupPayload::class.java)
                    if (!payload.periodId.isNullOrBlank() && !payload.groups.isNullOrEmpty()) {
                        val k: Either<String, List<RegistrationGroupDTO>> = payload.groups.fold(Either.right(emptyList())) { either, group ->
                            either.flatMap { existingGroups ->
                                if (!group?.displayName.isNullOrBlank() && !group?.registrationInfoId.isNullOrBlank()) {
                                    val groupId = IDGenerator.hashString("${group.registrationInfoId}/${group.displayName}")
                                    val regInfoId = group.registrationInfoId ?: command.competitionId
                                    val periodGroups = jooqQueries.fetchRegistrationGroupIdsByPeriodIdAndRegistrationInfoId(regInfoId, payload.periodId).collectList().block()
                                    val defaultGroup = group?.defaultGroup?.let {
                                        if (it) {
                                            jooqQueries.fetchDefaulRegistrationGroupByCompetitionIdAndIdNeq(regInfoId, groupId).block()
                                        } else {
                                            null
                                        }
                                    }
                                    if (defaultGroup != null) {
                                        Either.left("There is already a default group for competition ${command.competitionId} with different id: ${defaultGroup.displayName
                                                ?: "<Unknown>"}, ${defaultGroup.id}")
                                    } else {
                                        if (registrationPeriodCrudRepository.existsById(payload.periodId)) {
                                            if (periodGroups?.any { it == groupId } != true) {
                                                Either.right(existingGroups + group.setId(groupId).setRegistrationInfoId(regInfoId))
                                            } else {
                                                Either.left("Group with id $groupId already exists")
                                            }
                                        } else {
                                            Either.left("Cannot find period with ID: ${payload.periodId}")
                                        }
                                    }
                                } else {
                                    Either.left("Group name is not specified ${group?.displayName}.")
                                }
                            }
                        }
                        k.fold({
                            log.error(it)
                            listOf(createErrorEvent(it))
                        }, { listOf(createEvent(EventType.REGISTRATION_GROUP_ADDED, RegistrationGroupAddedPayload(payload.periodId, it.toTypedArray()))) })
                    } else {
                        listOf(createErrorEvent("Period Id is not specified or no groups to add"))
                    }
                }
                CommandType.ADD_REGISTRATION_PERIOD_COMMAND -> {
                    val payload = mapper.convertValue(command.payload, AddRegistrationPeriodPayload::class.java)
                    if (payload.period != null && !command.competitionId.isNullOrBlank()) {
                        val regInfo = registrationInfoCrudRepository.findById(command.competitionId)
                        val periodId = IDGenerator.hashString("${command.competitionId}/${payload.period.name}")
                        regInfo?.let {
                            if (!registrationPeriodCrudRepository.existsById(periodId)) {
                                listOf(createEvent(EventType.REGISTRATION_PERIOD_ADDED, RegistrationPeriodAddedPayload(payload.period.setId(periodId))))
                            } else {
                                listOf(createErrorEvent("Period with id ${payload.period.id} already exists."))
                            }
                        } ?: try {
                            listOf(createEvent(EventType.REGISTRATION_PERIOD_ADDED, RegistrationPeriodAddedPayload(payload.period.setId(periodId))))
                        } catch (e: Throwable) {
                            log.error("Exception.", e)
                            listOf(createErrorEvent("Registration info is missing for the competition ${command.competitionId}, exception when adding: $e"))
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
                            if (newProperties.creationTimestamp == null) {
                                newProperties.creationTimestamp = System.currentTimeMillis()
                            }
                            if (newProperties.status == null) {
                                newProperties.status = CompetitionStatus.CREATED
                            }
                            if (newProperties.timeZone.isNullOrBlank() || newProperties.timeZone == "null") {
                                newProperties.timeZone = ZoneId.systemDefault().id
                            }
                            if (!competitionPropertiesCrudRepository.existsById(command.competitionId)) {
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
                    val state = competitionPropertiesCrudRepository.findById(command.competitionId)
                    if (state?.bracketsPublished != true) {
                        jooqQueries.getCategoryIdsForCompetition(command.competitionId).map { cat -> createEvent(EventType.CATEGORY_BRACKETS_DROPPED, command.payload).setCategoryId(cat) }.collectList().block()
                    } else {
                        listOf(createErrorEvent("Cannot drop brackets, they are already published."))
                    }
                }
                CommandType.DROP_SCHEDULE_COMMAND -> {
                    val state = competitionPropertiesCrudRepository.findById(command.competitionId)
                    if (state?.schedulePublished != true) {
                        listOf(createEvent(EventType.SCHEDULE_DROPPED, command.payload))
                    } else {
                        listOf(createErrorEvent("Cannot drop schedule, it is already published."))
                    }
                }
                CommandType.GENERATE_SCHEDULE_COMMAND -> {
                    val payload = command.payload!!
                    val periods = mapper.convertValue(payload, Array<PeriodDTO>::class.java)?.map { it.setId(it.id ?: IDGenerator.createPeriodId(command.competitionId)) }
                    val compProps = competitionPropertiesCrudRepository.findById(command.competitionId)
                    val categories = periods?.flatMap {
                        it.scheduleEntries?.toList() ?: emptyList()
                    }?.flatMap { it.categoryIds?.toList() ?: emptyList() }?.distinct()
                    val missingCategories = categories?.fold(emptyList<String>(), { acc, cat ->
                        if (categoryCrudRepository.existsById(cat)) {
                            acc
                        } else {
                            acc + cat
                        }
                    })
                    if (compProps != null && !compProps.schedulePublished && !periods.isNullOrEmpty()) {
                        if (missingCategories.isNullOrEmpty()) {
                            val competitorNumbersByCategoryIds = jooqQueries.getCompetitorNumbersByCategoryIds(command.competitionId)
                            val schedule = scheduleService.generateSchedule(command.competitionId, periods,
                                    getAllBrackets(command.competitionId),
                                    compProps.timeZone,
                                    competitorNumbersByCategoryIds)
                            val newFights = schedule.periods?.flatMap { period ->
                                period.mats?.flatMap { it.fightStartTimes.map { f -> f.setPeriodId(period.id) } }
                                        ?: emptyList()
                            }?.toTypedArray()
                            val fightStartTimeUpdatedPayload = FightStartTimeUpdatedPayload().setNewFights(newFights)

                            listOf(createEvent(EventType.SCHEDULE_GENERATED, ScheduleGeneratedPayload(schedule)), createEvent(EventType.FIGHTS_START_TIME_UPDATED, fightStartTimeUpdatedPayload))
                        } else {
                            listOf(createErrorEvent("Categories $missingCategories are unknown"))
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