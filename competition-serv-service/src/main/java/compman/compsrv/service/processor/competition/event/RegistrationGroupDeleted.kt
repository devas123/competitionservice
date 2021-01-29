package compman.compsrv.service.processor.competition.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationGroupDeletedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class RegistrationGroupDeleted(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Competition>, ValidatedEventExecutor<Competition>(mapper, validators) {
    override fun applyEvent(
            aggregate: Competition,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competition {
        return executeValidated<RegistrationGroupDeletedPayload, Competition>(event) { payload, _ ->
            aggregate.registrationGroupDeleted(payload)
        }.unwrap(event)
    }

    fun Competition.registrationGroupDeleted(payload: RegistrationGroupDeletedPayload): Competition {
        registrationInfo.registrationPeriods.find { it.id == payload.periodId }?.let { per ->
            per.registrationGroupIds = per.registrationGroupIds.filter { it != payload.groupId }.toTypedArray()
        }
        registrationInfo.registrationGroups = registrationInfo.registrationGroups.filter { it.id != payload.groupId }.toTypedArray()
        return this
    }

    override val eventType: EventType
        get() = EventType.REGISTRATION_GROUP_DELETED
}