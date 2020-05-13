package compman.compsrv.service.processor.event

import com.compmanager.compservice.jooq.tables.daos.*
import com.compmanager.compservice.jooq.tables.pojos.RegGroupRegPeriod
import com.compmanager.compservice.jooq.tables.pojos.RegistrationInfo
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.mapping.toPojo
import compman.compsrv.model.dto.competition.CompetitionStateDTO
import compman.compsrv.model.dto.competition.RegistrationInfoDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.JooqRepository
import compman.compsrv.service.CompetitionCleaner
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.applyProperties
import org.springframework.stereotype.Component

@Component
class CompetitionEventProcessor(private val competitionPropertiesDao: CompetitionPropertiesDao,
                                private val registrationGroupCrudRepository: RegistrationGroupDao,
                                private val registrationPeriodCrudRepository: RegistrationPeriodDao,
                                private val regGroupRegPeriodDao: RegGroupRegPeriodDao,
                                private val registrationInfoCrudRepository: RegistrationInfoDao,
                                private val jooqRepository: JooqRepository,
                                private val competitionCleaner: CompetitionCleaner,
                                mapper: ObjectMapper,
                                validators: List<PayloadValidator>) : AbstractEventProcessor(mapper, validators) {
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
                EventType.REGISTRATION_INFO_UPDATED,
                EventType.INTERNAL_COMPETITION_INFO
        )
    }

    override fun applyEvent(event: EventDTO) {
        fun createError(error: String) = EventApplyingException(error, event)
        when (event.type) {
            EventType.REGISTRATION_INFO_UPDATED -> executeValidated(event, RegistrationInfoUpdatedPayload::class.java) { payload, _ ->
                payload.registrationInfo?.let {
                    kotlin.runCatching {
                        jooqRepository.updateRegistrationInfo(it)
                    }.recover { e ->
                        log.error("Error while executing operation.", e)
                    }
                }
            }
            EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED -> executeValidated(event, RegistrationGroupCategoriesAssignedPayload::class.java) { payload, _ ->
                jooqRepository.updateRegistrationGroupCategories(payload.groupId!!, payload.categories?.toList()!!)
            }
            EventType.REGISTRATION_GROUP_ADDED -> executeValidated(event, RegistrationGroupAddedPayload::class.java) { payload, _ ->
                val regPeriod = registrationPeriodCrudRepository.findById(payload.periodId)
                if (regPeriod != null && !payload.groups.isNullOrEmpty()) {
                    jooqRepository.addRegistrationGroupsToPeriod(payload.periodId, payload.groups.toList())
                } else {
                    log.error("Didn't find period with id ${payload.periodId} or groups is empty")
                    throw createError("Didn't find period with id ${payload.periodId} or groups is empty")
                }
            }
            EventType.REGISTRATION_GROUP_DELETED -> executeValidated(event, RegistrationGroupDeletedPayload::class.java) { payload, _ ->
                if (registrationPeriodCrudRepository.existsById(payload.periodId)) {
                    if (regGroupRegPeriodDao.fetchByRegGroupId(payload.groupId).size > 1) {
                        jooqRepository.deleteRegGroupRegPeriodById(payload.groupId, payload.periodId)
                    } else {
                        registrationGroupCrudRepository.deleteById(payload.groupId)
                    }
                } else {
                    log.error("Didn't find period with id ${payload.periodId}")
                    throw createError("Didn't find period with id ${payload.periodId}")
                }
            }
            EventType.REGISTRATION_PERIOD_ADDED -> executeValidated(event, RegistrationPeriodAddedPayload::class.java) { payload, _ ->
                if (registrationInfoCrudRepository.existsById(event.competitionId)) {
                    jooqRepository.saveRegistrationPeriod(payload.period)
                }
            }
            EventType.REGISTRATION_PERIOD_DELETED -> executeValidated(event, RegistrationPeriodDeletedPayload::class.java) { payload, _ ->
                registrationPeriodCrudRepository.deleteById(payload.periodId)
            }
            EventType.COMPETITION_DELETED -> {
                competitionCleaner.deleteCompetition(event.competitionId)
            }
            EventType.COMPETITION_CREATED -> executeValidated(event, CompetitionCreatedPayload::class.java) { payload, _ ->
                payload.properties?.let { props ->
                    log.info("Creating competition: $props")
                    val state = CompetitionStateDTO().setId(props.id).setProperties(props)
                    val regInfo = payload.reginfo ?: RegistrationInfoDTO()
                            .setId(props.id).setRegistrationOpen(false).setRegistrationGroups(emptyArray()).setRegistrationPeriods(emptyArray())
                    jooqRepository.saveCompetitionState(state)
                    registrationInfoCrudRepository.insert(RegistrationInfo(regInfo.id, regInfo.registrationOpen))
                    registrationPeriodCrudRepository.insert(regInfo.registrationPeriods.orEmpty().map { it.toPojo() })
                    registrationGroupCrudRepository.insert(regInfo.registrationGroups.orEmpty().map { it.toPojo() })
                    regGroupRegPeriodDao.insert(regInfo.registrationGroups?.flatMap { it.registrationPeriodIds.map { rp -> RegGroupRegPeriod(it.id, rp) } }.orEmpty())
                } ?: throw createError("Properties are missing.")
            }
            EventType.SCHEDULE_DROPPED -> {
                jooqRepository.deleteScheduleEntriesByCompetitionId(event.competitionId)
                jooqRepository.deleteScheduleRequirementsByCompetitionId(event.competitionId)
            }
            EventType.SCHEDULE_GENERATED -> executeValidated(event, ScheduleGeneratedPayload::class.java) { scheduleGeneratedPayload, _ ->
                if (scheduleGeneratedPayload.schedule != null) {
                    val schedule = scheduleGeneratedPayload.schedule
                    jooqRepository.saveSchedule(schedule)
                } else {
                    throw createError("Schedule not provided.")
                }
            }
            EventType.COMPETITION_PROPERTIES_UPDATED -> executeValidated(event, CompetitionPropertiesUpdatedPayload::class.java) { payload, _ ->
                val comp = competitionPropertiesDao.findById(event.competitionId)
                comp?.applyProperties(payload.properties)?.let {
                    jooqRepository.updateCompetitionProperties(it)
                }
            }
            EventType.INTERNAL_COMPETITION_INFO -> {
            }
            EventType.COMPETITION_STARTED, EventType.COMPETITION_STOPPED, EventType.COMPETITION_PUBLISHED, EventType.COMPETITION_UNPUBLISHED ->
                executeValidated(event, CompetitionStatusUpdatedPayload::class.java) { payload, _ ->
                    val status = payload.status
                    if (status != null) {
                        jooqRepository.updateCompetitionStatus(event.competitionId, status)
                    }
                }
            else -> {
                log.info("No handler for event: $event")
                throw EventApplyingException("No event handler for event ${event.type}", event)
            }
        }
    }
}