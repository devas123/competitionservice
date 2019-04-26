package compman.compsrv.service.processor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.competition.*
import compman.compsrv.jpa.es.events.EventHolder
import compman.compsrv.jpa.schedule.Schedule
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.*
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class CompetitionEventProcessor(private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                                private val eventCrudRepository: EventCrudRepository,
                                private val scheduleCrudRepository: ScheduleCrudRepository,
                                private val categoryCrudRepository: CategoryDescriptorCrudRepository,
                                private val bracketsCrudRepository: BracketsCrudRepository,
                                private val fightCrudRepository: FightCrudRepository,
                                private val registrationGroupCrudRepository: RegistrationGroupCrudRepository,
                                private val registrationPeriodCrudRepository: RegistrationPeriodCrudRepository,
                                private val registrationInfoCrudRepository: RegistrationInfoCrudRepository,
                                private val transactionTemplate: TransactionTemplate,
                                private val mapper: ObjectMapper) : IEventProcessor {
    override fun affectedEvents(): Set<EventType> {
        return setOf(
                EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED,
                EventType.REGISTRATION_GROUP_ADDED,
                EventType.REGISTRATION_GROUP_DELETED,
                EventType.REGISTRATION_PERIOD_ADDED,
                EventType.REGISTRATION_PERIOD_DELETED,
                EventType.COMPETITION_DELETED,
                EventType.COMPETITION_CREATED,
                EventType.DUMMY,
                EventType.SCHEDULE_DROPPED,
                EventType.ALL_BRACKETS_DROPPED,
                EventType.SCHEDULE_GENERATED,
                EventType.COMPETITION_PROPERTIES_UPDATED,
                EventType.COMPETITION_STARTED,
                EventType.COMPETITION_STOPPED,
                EventType.COMPETITION_PUBLISHED,
                EventType.COMPETITION_UNPUBLISHED,
                EventType.DASHBOARD_CREATED,
                EventType.DASHBOARD_DELETED,
                EventType.ERROR_EVENT

        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionEventProcessor::class.java)
    }

    private fun <T> getPayloadAs(payload: String?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.readValue(payload, clazz)
        }
        return null
    }

    override fun applyEvent(event: EventDTO): List<EventDTO> {
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
                EventType.DASHBOARD_DELETED -> {
                    val competitionState = competitionStateCrudRepository.findByIdOrNull(event.competitionId)
                    if (competitionState != null) {
                        competitionState.dashboardState = null
                        competitionStateCrudRepository.save(competitionState)
                        listOf(event)
                    } else {
                        listOf(createErrorEvent("Cannot load competition state for competition ${event.competitionId}"))
                    }

                }
                EventType.DASHBOARD_CREATED -> {
                    val payload = getPayloadAs(event.payload, DashboardCreatedPayload::class.java)
                    if (payload?.dashboardState != null) {
                        val competitionState = competitionStateCrudRepository.findByIdOrNull(event.competitionId)
                        if (competitionState != null) {
                            competitionState.dashboardState = CompetitionDashboardState.fromDTO(payload.dashboardState)
                            val newcompState = competitionStateCrudRepository.saveAndFlush(competitionState)
                            listOf(event.setPayload(mapper.writeValueAsString(DashboardCreatedPayload(newcompState.dashboardState?.toDTO()))))
                        } else {
                            listOf(createErrorEvent("Cannot load competition state for competition ${event.competitionId}"))
                        }
                    } else {
                        listOf(createErrorEvent("Cannot load dashboard state from event $event"))
                    }
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
                    competitionStateCrudRepository.deleteById(event.competitionId)
                    listOf(event)
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
                    val scheduleGeneratedPayload = getPayloadAs(event.payload, ScheduleGeneratedPayload::class.java)
                    if (scheduleGeneratedPayload?.schedule != null) {
                        val schedule = Schedule.fromDTO(scheduleGeneratedPayload.schedule, fightCrudRepository)
                        schedule.periods?.forEach {
                            if (!it.categories.isNullOrEmpty()) {
                                categoryCrudRepository.saveAll(it.categories)
                            }
                        }
                        scheduleCrudRepository.save(schedule)
                        listOf(event)
                    } else {
                        emptyList()
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
}