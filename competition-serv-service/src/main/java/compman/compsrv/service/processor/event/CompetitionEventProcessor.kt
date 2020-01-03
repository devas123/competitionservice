package compman.compsrv.service.processor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.mapping.toDTO
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.*
import compman.compsrv.util.applyProperties
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class CompetitionEventProcessor(private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                                private val scheduleCrudRepository: ScheduleCrudRepository,
                                private val competitorCrudRepository: CompetitorCrudRepository,
                                private val bracketsCrudRepository: BracketsCrudRepository,
                                private val fightCrudRepository: FightCrudRepository,
                                private val registrationGroupCrudRepository: RegistrationGroupCrudRepository,
                                private val registrationPeriodCrudRepository: RegistrationPeriodCrudRepository,
                                private val registrationInfoCrudRepository: RegistrationInfoCrudRepository,
                                private val mapper: ObjectMapper) : IEventProcessor<CompetitionState> {
    override fun affectedEvents(): Set<EventType> {
        return setOf(
                EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED,
                EventType.REGISTRATION_GROUP_CREATED,
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
                EventType.ERROR_EVENT,
                EventType.REGISTRATION_INFO_UPDATED,
                EventType.INTERNAL_COMPETITION_INFO
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

    override fun applyEvent(state: CompetitionState, event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
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
                EventType.INTERNAL_COMPETITION_INFO -> {
                    listOf(event)
                }
                EventType.REGISTRATION_INFO_UPDATED -> {
                    val payload = getPayloadAs(event.payload, RegistrationInfoUpdatedPayload::class.java)
                    payload?.registrationInfo?.let {
                        kotlin.runCatching {
                            val regInfo = it.toEntity()
                            listOf(event.setPayload(
                                    mapper.writeValueAsString(
                                            payload.setRegistrationInfo(
                                                    registrationInfoCrudRepository.save(regInfo).toDTO()))))
                        }.recover { e ->
                            log.error("Error while executing operation.", e)
                            listOf(event)
                        }.getOrDefault(emptyList())
                    } ?: listOf(createErrorEvent("Registration info is null."))
                }
                EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED ->  {
                    val payload = getPayloadAs(event.payload, RegistrationGroupCategoriesAssignedPayload::class.java)
                    if (payload != null) {
                        val group = registrationGroupCrudRepository.findByIdOrNull(payload.groupId)
                        if (group != null) {
                            group.categories = payload.categories.toMutableSet()
                            listOf(event.setPayload(mapper.writeValueAsString(RegistrationGroupCategoriesAssignedPayload(payload.periodId, payload.groupId,
                                    registrationGroupCrudRepository.save(group).categories?.toTypedArray()))))
                        } else {
                            listOf(createErrorEvent("Registration group not found."))
                        }
                    } else {
                        listOf(createErrorEvent("Payload is null."))
                    }
                }
                EventType.REGISTRATION_GROUP_CREATED ->  {
                    val payload = getPayloadAs(event.payload, RegistrationGroupAddedPayload::class.java)!!
                    val regPeriod = registrationPeriodCrudRepository.findByIdOrNull(payload.periodId)
                    if (regPeriod != null) {
                        val group = payload.group.toEntity { registrationInfoCrudRepository.findById(event.competitionId).orElseThrow { EventApplyingException("registration info with id ${event.competitionId} not found", event) } }
                        group.registrationPeriods = mutableSetOf(regPeriod)
                        if (regPeriod.registrationGroups != null) {
                            regPeriod.registrationGroups!!.add(group)
                        } else {
                            regPeriod.registrationGroups = mutableSetOf(group)
                        }
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
                    val period = registrationPeriodCrudRepository.findById(payload.periodId)
                    period.map {
                        it.registrationGroups?.removeIf { gr -> gr.id == payload.groupId }
                        registrationPeriodCrudRepository.save(it)
                        if (registrationGroupCrudRepository.findByIdOrNull(payload.groupId)?.registrationPeriods.isNullOrEmpty()) {
                            registrationGroupCrudRepository.deleteById(payload.groupId)
                        }
                        listOf(event)
                    }.orElseGet {
                        log.error("Didn't find period with id ${payload.periodId}")
                        emptyList()
                    }
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
                EventType.DASHBOARD_CREATED ->  {
                    val payload = getPayloadAs(event.payload, DashboardCreatedPayload::class.java)
                    if (payload?.dashboardState != null) {
                        val competitionState = competitionStateCrudRepository.findByIdOrNull(event.competitionId)
                        if (competitionState != null) {
                            competitionState.dashboardState = payload.dashboardState.toEntity()
                            val newcompState = competitionStateCrudRepository.save(competitionState)
                            listOf(event.setPayload(mapper.writeValueAsString(DashboardCreatedPayload(newcompState.dashboardState?.toDTO()))))
                        } else {
                            listOf(createErrorEvent("Cannot load competition state for competition ${event.competitionId}"))
                        }
                    } else {
                        listOf(createErrorEvent("Cannot load dashboard state from event $event"))
                    }
                }
                EventType.REGISTRATION_PERIOD_ADDED ->  {
                    val payload = getPayloadAs(event.payload, RegistrationPeriodAddedPayload::class.java)!!
                    val info = registrationInfoCrudRepository.findByIdOrNull(event.competitionId)
                    if (info != null) {
                        val period = payload.period.toEntity({ id -> registrationGroupCrudRepository.findById(id).orElseThrow { EventApplyingException("Cannot get registration group with id $id", event) } },
                                { id -> registrationInfoCrudRepository.findById(id).orElseThrow { EventApplyingException("Cannot get registration info with id $id", event) } })
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
                EventType.COMPETITION_DELETED ->  {
                    if (competitionStateCrudRepository.existsById(event.competitionId)) {
                        competitionStateCrudRepository.deleteById(event.competitionId)
                    }
                    listOf(event)
                }
                EventType.COMPETITION_CREATED ->  {
                    val payload = getPayloadAs(event.payload, CompetitionCreatedPayload::class.java)
                    payload?.properties?.let { props ->
                        val state = CompetitionState(props.id, props.toEntity())
                        state.properties?.registrationInfo?.let {
                            registrationInfoCrudRepository.save(it)
                        }
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
                EventType.SCHEDULE_GENERATED ->  {
                    val scheduleGeneratedPayload = getPayloadAs(event.payload, ScheduleGeneratedPayload::class.java)
                    if (scheduleGeneratedPayload?.schedule != null) {
                        val schedule = scheduleGeneratedPayload.schedule?.toEntity ({ fightId -> fightCrudRepository.getOne(fightId) }, { competitorId -> competitorCrudRepository.findByIdOrNull(competitorId)})
                        schedule?.let {
                            scheduleCrudRepository.save(schedule)
                            listOf(event)
                        } ?: listOf(createErrorEvent("Schedule not found."))
                    } else {
                        listOf(createErrorEvent("Schedule not provided."))
                    }
                }
                EventType.COMPETITION_PROPERTIES_UPDATED -> {
                    val payload = getPayloadAs(event.payload, CompetitionPropertiesUpdatedPayload::class.java)
                    val comp = competitionStateCrudRepository.getOne(event.competitionId)
                    comp.properties = comp.properties.applyProperties(payload?.properties)
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
            ns ?: emptyList()
        } catch (e: Exception) {
            log.error("Error while applying event.", e)
            listOf(createErrorEvent(e.localizedMessage))
        }
    }
}