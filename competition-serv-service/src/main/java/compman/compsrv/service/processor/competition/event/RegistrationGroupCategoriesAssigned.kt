package compman.compsrv.service.processor.competition.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationGroupCategoriesAssignedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class RegistrationGroupCategoriesAssigned(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Competition>, ValidatedEventExecutor<Competition>(mapper, validators) {
    override fun applyEvent(
            aggregate: Competition?,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competition? = aggregate?.let {
        executeValidated<RegistrationGroupCategoriesAssignedPayload, Competition>(event) { payload, _ ->
            aggregate.registrationGroupCategoriesAssigned(payload)
        }.unwrap(event)
    }

    fun Competition.registrationGroupCategoriesAssigned(payload: RegistrationGroupCategoriesAssignedPayload): Competition {
        this.registrationInfo.registrationGroups
                ?.find { it.id == payload.groupId && it.registrationPeriodIds?.contains(payload.periodId) == true }
                ?.categories = payload.categories
        return this
    }

    override val eventType: EventType
        get() = EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED
}