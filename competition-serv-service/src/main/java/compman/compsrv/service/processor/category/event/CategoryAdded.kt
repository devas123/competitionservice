package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CategoryAddedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class CategoryAdded(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
            aggregate: Category,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Category {
        return executeValidated<CategoryAddedPayload, Category>(event) { payload, _ ->
            aggregate.categoryAdded(payload)
        }.unwrap(event)
    }

    fun Category.categoryAdded(payload: CategoryAddedPayload): Category {
        return this.copy(descriptor = payload.categoryState, id = payload.categoryState.id)
    }

    override val eventType: EventType
        get() = EventType.CATEGORY_ADDED
}