package compman.compsrv.service.processor.competition.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationPeriodDeletedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class RegistrationPeriodDeleted(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Competition>, ValidatedEventExecutor<Competition>(mapper, validators) {
    override fun applyEvent(
            aggregate: Competition,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competition {
        return executeValidated<RegistrationPeriodDeletedPayload, Competition>(event) { payload, _ ->
            aggregate.registrationPeriodDeleted(payload)
        }.unwrap(event)
    }

    fun Competition.registrationPeriodDeleted(payload: RegistrationPeriodDeletedPayload): Competition {
        registrationInfo.registrationPeriods = registrationInfo.registrationPeriods.filter { it.id != payload.periodId }.toTypedArray()
        registrationInfo.registrationGroups.forEach { it.registrationPeriodIds = it.registrationPeriodIds.filter { id -> id != payload.periodId }.toTypedArray() }
        return this
    }

    override val eventType: EventType
        get() = EventType.REGISTRATION_PERIOD_DELETED
}