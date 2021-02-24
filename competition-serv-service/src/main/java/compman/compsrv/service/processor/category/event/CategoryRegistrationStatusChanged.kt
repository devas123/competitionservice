package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.commands.payload.CategoryRegistrationStatusChangePayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class CategoryRegistrationStatusChanged(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
            aggregate: Category?,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Category? = aggregate?.let {
        executeValidated<CategoryRegistrationStatusChangePayload, Category>(event) { payload, _ ->
            aggregate.registrationStatusChanged(payload)
        }.unwrap(event)
    } ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.registrationStatusChanged(payload: CategoryRegistrationStatusChangePayload): Category {
        this.descriptor.registrationOpen = payload.isNewStatus
        return this
    }

    override val eventType: EventType
        get() = EventType.CATEGORY_REGISTRATION_STATUS_CHANGED
}