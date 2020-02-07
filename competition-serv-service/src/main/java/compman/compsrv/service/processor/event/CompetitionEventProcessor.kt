package compman.compsrv.service.processor.event

import com.compmanager.compservice.jooq.tables.daos.*
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.mapping.toDTO
import compman.compsrv.model.dto.competition.CompetitionStateDTO
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.JooqQueries
import compman.compsrv.service.CompetitionCleaner
import compman.compsrv.util.applyProperties
import compman.compsrv.util.getPayloadFromString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class CompetitionEventProcessor(private val competitionPropertiesDao: CompetitionPropertiesDao,
                                private val registrationGroupCrudRepository: RegistrationGroupDao,
                                private val registrationPeriodCrudRepository: RegistrationPeriodDao,
                                private val regGroupRegPeriodDao: RegGroupRegPeriodDao,
                                private val registrationInfoCrudRepository: RegistrationInfoDao,
                                private val jooqQueries: JooqQueries,
                                private val competitionCleaner: CompetitionCleaner,
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

    private fun <T> getPayloadAs(payload: String?, clazz: Class<T>): T? = mapper.getPayloadFromString(payload, clazz)

    override fun applyEvent(event: EventDTO): List<EventDTO> {
        fun createError(error: String) = EventApplyingException(error, event)
        try {
            when (event.type) {
               EventType.REGISTRATION_INFO_UPDATED -> {
                    val payload = getPayloadAs(event.payload, RegistrationInfoUpdatedPayload::class.java)
                    payload?.registrationInfo?.let {
                        kotlin.runCatching {
                            jooqQueries.updateRegistrationInfo(it)
                        }.recover { e ->
                            log.error("Error while executing operation.", e)
                        }
                    }
                }
                EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED -> {
                    val payload = getPayloadAs(event.payload, RegistrationGroupCategoriesAssignedPayload::class.java)
                    if (payload != null) {
                        jooqQueries.updateRegistrationGroupCategories(payload.groupId!!, payload.categories?.toList()!!)
                    } else {
                        throw createError("Payload is null.")
                    }
                }
                EventType.REGISTRATION_GROUP_ADDED -> {
                    val payload = getPayloadAs(event.payload, RegistrationGroupAddedPayload::class.java)!!
                    val regPeriod = registrationPeriodCrudRepository.findById(payload.periodId)
                    if (regPeriod != null && !payload.groups.isNullOrEmpty()) {
                        jooqQueries.addRegistrationGroupsToPeriod(payload.periodId, payload.groups.toList())
                    } else {
                        log.error("Didn't find period with id ${payload.periodId} or groups is empty")
                        throw createError("Didn't find period with id ${payload.periodId} or groups is empty")
                    }
                }
                EventType.REGISTRATION_GROUP_DELETED -> {
                    val payload = getPayloadAs(event.payload, RegistrationGroupDeletedPayload::class.java)!!
                    if (registrationPeriodCrudRepository.existsById(payload.periodId)) {
                        if (regGroupRegPeriodDao.fetchByRegGroupId(payload.groupId).size > 1) {
                            jooqQueries.deleteRegGroupRegPeriodById(payload.groupId, payload.periodId)
                        } else {
                            registrationGroupCrudRepository.deleteById(payload.groupId)
                        }
                    } else {
                        log.error("Didn't find period with id ${payload.periodId}")
                        throw createError("Didn't find period with id ${payload.periodId}")
                    }
                }
                EventType.DASHBOARD_DELETED -> {
                    if (competitionPropertiesDao.existsById(event.competitionId)) {
                        jooqQueries.deleteDashboardPeriodsByCompetitionId(event.competitionId)
                    } else {
                        throw createError("Cannot load competition state for competition ${event.competitionId}")
                    }

                }
                EventType.DASHBOARD_CREATED -> {
                    val payload = getPayloadAs(event.payload, DashboardCreatedPayload::class.java)
                    if (payload?.dashboardState != null) {
                        if (competitionPropertiesDao.existsById(event.competitionId)) {
                            payload.dashboardState.periods?.toList()?.let {
                                jooqQueries.saveDashboardPeriods(event.competitionId, it)
                            }
                        } else {
                            throw createError("Cannot load competition state for competition ${event.competitionId}")
                        }
                    } else {
                        throw createError("Cannot load dashboard state from event $event")
                    }
                }
                EventType.REGISTRATION_PERIOD_ADDED -> {
                    val payload = getPayloadAs(event.payload, RegistrationPeriodAddedPayload::class.java)!!
                    if (registrationInfoCrudRepository.existsById(event.competitionId)) {
                        jooqQueries.saveRegistrationPeriod(payload.period)
                    }
                }
                EventType.REGISTRATION_PERIOD_DELETED -> {
                    val payload = getPayloadAs(event.payload, String::class.java)
                    registrationPeriodCrudRepository.deleteById(payload!!)
                }
                EventType.COMPETITION_DELETED -> {
                    competitionCleaner.deleteCompetition(event.competitionId)
                }
                EventType.COMPETITION_CREATED -> {
                    val payload = getPayloadAs(event.payload, CompetitionCreatedPayload::class.java)
                    payload?.properties?.let { props ->
                        val state = CompetitionStateDTO().setId(props.id).setProperties(props)
                        jooqQueries.saveCompetitionState(state)
                    } ?: throw createError("Properties are missing.")
                }
                EventType.SCHEDULE_DROPPED -> {
                    jooqQueries.deleteScheudlePeriodsByCompetitionId(event.competitionId)
                }
                EventType.SCHEDULE_GENERATED -> {
                    val scheduleGeneratedPayload = getPayloadAs(event.payload, ScheduleGeneratedPayload::class.java)
                    if (scheduleGeneratedPayload?.schedule != null) {
                        val schedule = scheduleGeneratedPayload.schedule
                        jooqQueries.saveSchedule(schedule)
                    } else {
                        throw createError("Schedule not provided.")
                    }
                }
                EventType.COMPETITION_PROPERTIES_UPDATED -> {
                    val payload = getPayloadAs(event.payload, CompetitionPropertiesUpdatedPayload::class.java)
                    val comp = competitionPropertiesDao.findById(event.competitionId)?.toDTO(emptyArray(), emptyArray()) {null}
                    comp?.applyProperties(payload?.properties)?.let {
                        jooqQueries.updateCompetitionProperties(it)
                    }
                }
                in listOf(EventType.COMPETITION_STARTED, EventType.COMPETITION_STOPPED, EventType.COMPETITION_PUBLISHED, EventType.COMPETITION_UNPUBLISHED) -> {
                    val status = getPayloadAs(event.payload, CompetitionStatus::class.java)
                    if (status != null) {
                        jooqQueries.updateCompetitionStatus(event.competitionId, status)
                    }
                }
                else -> {
                    log.info("No handler for event: $event")
                }
            }
        } catch (e: Exception) {
            log.error("Error while applying event.", e)
            throw createError(e.localizedMessage)
        }
        return listOf(event)
    }
}