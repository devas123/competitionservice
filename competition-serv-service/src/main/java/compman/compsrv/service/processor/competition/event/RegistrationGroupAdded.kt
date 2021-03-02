package compman.compsrv.service.processor.competition.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationGroupAddedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class RegistrationGroupAdded(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Competition>, ValidatedEventExecutor<Competition>(mapper, validators) {
    override fun applyEvent(
            aggregate: Competition?,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competition? = aggregate?.let {
        executeValidated<RegistrationGroupAddedPayload, Competition>(event) { payload, _ ->
            aggregate.registrationGroupAdded(payload)
        }.unwrap(event)
    }

    fun Competition.registrationGroupAdded(payload: RegistrationGroupAddedPayload): Competition {
        val regPeriod = registrationInfo.registrationPeriods?.find { it.id == payload.periodId }
        if (regPeriod != null && !payload.groups.isNullOrEmpty()) {
            regPeriod.registrationGroupIds = (regPeriod.registrationGroupIds ?: emptyArray()) + payload.groups.mapNotNull { it.id }.toTypedArray()
            registrationInfo.registrationGroups = (registrationInfo.registrationGroups ?: emptyArray()) + payload.groups
        }
        return this
    }

    override val eventType: EventType
        get() = EventType.REGISTRATION_GROUP_ADDED
}