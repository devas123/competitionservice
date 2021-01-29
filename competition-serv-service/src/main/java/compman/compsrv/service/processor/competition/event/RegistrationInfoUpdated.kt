package compman.compsrv.service.processor.competition.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationInfoUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class RegistrationInfoUpdated(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Competition>, ValidatedEventExecutor<Competition>(mapper, validators) {
    override fun applyEvent(
            aggregate: Competition,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competition {
        return executeValidated<RegistrationInfoUpdatedPayload, Competition>(event) { payload, _ ->
            aggregate.registrationInfoUpdated(payload)
        }.unwrap(event)
    }

    fun Competition.registrationInfoUpdated(payload: RegistrationInfoUpdatedPayload): Competition {
        this.registrationInfo.registrationGroups = payload.registrationInfo.registrationGroups
        this.registrationInfo.registrationOpen = payload.registrationInfo.registrationOpen
        this.registrationInfo.registrationPeriods = payload.registrationInfo.registrationPeriods
        return this
    }

    override val eventType: EventType
        get() = EventType.REGISTRATION_INFO_UPDATED
}