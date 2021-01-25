package compman.compsrv.aggregate

import arrow.core.Either
import arrow.core.extensions.either.monad.monad
import arrow.core.extensions.list.foldable.foldM
import arrow.core.fix
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.dto.competition.RegistrationGroupDTO
import compman.compsrv.model.dto.competition.RegistrationInfoDTO
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.PeriodDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.service.schedule.ScheduleService
import compman.compsrv.service.schedule.StageGraph
import compman.compsrv.util.IDGenerator
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

class Competition(val id: String, val properties: CompetitionPropertiesDTO, private val registrationInfo: RegistrationInfoDTO, val categories: Array<String> = emptyArray(), val competitors: Array<String> = emptyArray(),
                  private val periods: Array<PeriodDTO> = emptyArray(), private val mats: Array<MatDescriptionDTO> = emptyArray()) : AbstractAggregate(AtomicLong(0), AtomicLong(0)) {
    fun process(payload: UpdateRegistrationInfoPayload, command: CommandDTO, createEvent: (CommandDTO, EventType, Payload?) -> EventDTO): List<EventDTO> {
        if (!payload.registrationInfo?.id.isNullOrBlank() && registrationInfo.id == payload.registrationInfo.id) {
            return listOf(createEvent(command, EventType.REGISTRATION_INFO_UPDATED, RegistrationInfoUpdatedPayload(payload.registrationInfo)))
        } else {
            throw IllegalArgumentException("Registration info not provided, or does not exist for id ${payload.registrationInfo?.id}")
        }
    }

    fun process(payload: AssignRegistrationGroupCategoriesPayload, command: CommandDTO, createEvent: (CommandDTO, EventType, Payload?) -> EventDTO): List<EventDTO> {
        return if (periodExists(payload.periodId)) {
            if (groupExists(payload.groupId)) {
                listOf(createEvent(command, EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED, RegistrationGroupCategoriesAssignedPayload(payload.periodId, payload.groupId, payload.categories)))
            } else {
                throw IllegalArgumentException("Unknown group id: ${payload.groupId}")
            }
        } else {
            throw IllegalArgumentException("Unknown period id: ${payload.periodId}")
        }
    }

    private fun groupExists(id: String) = registrationInfo.registrationGroups?.any { it.id == id } == true
    private fun periodExists(id: String) = registrationInfo.registrationPeriods?.any { it.id == id } == true

    fun process(payload: DeleteRegistrationGroupPayload, com: CommandDTO, createEvent: (CommandDTO, EventType, Payload?) -> EventDTO): List<EventDTO> {
        return if (groupExists(payload.groupId)
                && periodExists(payload.periodId)) {
            listOf(createEvent(com, EventType.REGISTRATION_GROUP_DELETED, RegistrationGroupDeletedPayload(payload.periodId, payload.groupId)))
        } else {
            throw IllegalArgumentException("Group does not exist.")
        }
    }

    fun process(payload: AddRegistrationGroupPayload, com: CommandDTO, createEvent: (CommandDTO, EventType, Payload?) -> EventDTO): List<EventDTO> {
        return if (!payload.periodId.isNullOrBlank() && !payload.groups.isNullOrEmpty()) {
            val groupsList = payload.groups.toList()
            val k = groupsList.foldM(Either.monad(), emptyList<RegistrationGroupDTO>()) { acc, group ->
                if (!group?.displayName.isNullOrBlank() && !group?.registrationInfoId.isNullOrBlank()) {
                    val groupId = IDGenerator.hashString("${group.registrationInfoId}/${group.displayName}")
                    val regInfoId = group.registrationInfoId ?: com.competitionId
                    val periodGroups = registrationInfo.registrationGroups?.filter { it.registrationPeriodIds.contains(payload.periodId) }
                    val defaultGroup = group?.defaultGroup?.let {
                        if (it) {
                            registrationInfo.registrationGroups?.find { group -> group.id == groupId }
                        } else {
                            null
                        }
                    }
                    if (defaultGroup != null) {
                        Either.left("There is already a default group for competition ${com.competitionId} with different id: ${defaultGroup.displayName ?: "<Unknown>"}, ${defaultGroup.id}")
                    } else {
                        if (registrationInfo.registrationPeriods?.any { it.id == payload.periodId } == true) {
                            if (periodGroups?.any { it.id == groupId } != true) {
                                Either.right(acc + group.setId(groupId).setRegistrationInfoId(regInfoId))
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
            }.fix()
            k.fold({
                throw IllegalArgumentException(it)
            }, { listOf(createEvent(com, EventType.REGISTRATION_GROUP_ADDED, RegistrationGroupAddedPayload(payload.periodId, it.toTypedArray()))) })
        } else {
            throw IllegalArgumentException("Period Id is not specified or no groups to add")
        }
    }

    fun process(payload: AddRegistrationPeriodPayload, com: CommandDTO, createEvent: (CommandDTO, EventType, Payload?) -> EventDTO): List<EventDTO> {
        return if (payload.period != null) {
            val periodId = IDGenerator.hashString("${com.competitionId}/${payload.period.name}")
            if (registrationInfo.registrationPeriods?.any { it.id == periodId } != true) {
                listOf(createEvent(com, EventType.REGISTRATION_PERIOD_ADDED, RegistrationPeriodAddedPayload(payload.period.setId(periodId))))
            } else {
                throw IllegalArgumentException("Period with id ${payload.period.id} already exists.")
            }
        } else {
            throw IllegalArgumentException("Period is not specified or competition id is missing.")
        }
    }

    fun process(payload: CreateCompetitionPayload, com: CommandDTO, createEvent: (command: CommandDTO, eventType: EventType, payload: Payload?) -> EventDTO): List<EventDTO> {
        val newProperties = payload.properties
        return if (newProperties != null) {
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
                listOf(createEvent(com, EventType.COMPETITION_CREATED, CompetitionCreatedPayload(
                        newProperties.setId(com.competitionId), payload.reginfo?.setId(com.competitionId))))
            } else {
                throw IllegalArgumentException("Competition name is empty")
            }
        } else {
            throw IllegalArgumentException("Cannot create competition, no properties provided")
        }
    }

    fun process(payload: GenerateSchedulePayload, com: CommandDTO, scheduleService: ScheduleService, allBrackets: Mono<StageGraph>, competitorNumbersByCategoryIds: Map<String, Int>, createEvent: (command: CommandDTO, eventType: EventType, payload: Payload?) -> EventDTO): List<EventDTO> {
        val periods = payload.periods?.toList()
        val mats = payload.mats?.map {
            it.setId(it.id ?: IDGenerator.createMatId(it.periodId))
        }!!
        val compProps = properties
        val categories = periods?.flatMap {
            it.scheduleEntries?.toList().orEmpty()
        }?.flatMap { it.categoryIds?.toList().orEmpty() }?.distinct()
        val missingCategories = categories?.fold(emptyList<String>(), { acc, cat ->
            if (categories.contains(cat)) {
                acc
            } else {
                acc + cat
            }
        })
        return if (!compProps.schedulePublished && !periods.isNullOrEmpty()) {
            if (missingCategories.isNullOrEmpty()) {
                val tuple = scheduleService.generateSchedule(com.competitionId, periods, mats,
                        allBrackets,
                        compProps.timeZone,
                        competitorNumbersByCategoryIds)
                val schedule = tuple.a
                val newFights = tuple.b
                val fightStartTimeUpdatedEvents = newFights.chunked(100) { list ->
                    val fightStartTimeUpdatedPayload = FightStartTimeUpdatedPayload().setNewFights(list.toTypedArray())
                    createEvent(com, EventType.FIGHTS_START_TIME_UPDATED, fightStartTimeUpdatedPayload)
                }
                listOf(createEvent(com, EventType.SCHEDULE_GENERATED, ScheduleGeneratedPayload(schedule))) + fightStartTimeUpdatedEvents
            } else {
                throw IllegalArgumentException("Categories $missingCategories are unknown")
            }
        } else {
            throw IllegalArgumentException("Could not find competition with ID: ${com.competitionId}")
        }
    }

    fun registrationGroupCategoriesAssigned(payload: RegistrationGroupCategoriesAssignedPayload): Competition {
        this.registrationInfo.registrationGroups
            ?.find { it.id == payload.groupId && it.registrationPeriodIds?.contains(payload.periodId) == true }
            ?.categories = payload.categories
        return this
    }

    fun registrationInfoUpdated(payload: RegistrationInfoUpdatedPayload): Competition {
        this.registrationInfo.registrationGroups = payload.registrationInfo.registrationGroups
        this.registrationInfo.registrationOpen = payload.registrationInfo.registrationOpen
        this.registrationInfo.registrationPeriods = payload.registrationInfo.registrationPeriods
        return this
    }
}