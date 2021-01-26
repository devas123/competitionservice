package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.BracketsGeneratedPayload
import compman.compsrv.model.exceptions.CategoryNotFoundException
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class BracketsGenerated(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
            aggregate: Category,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Category {
        return executeValidated<BracketsGeneratedPayload, Category>(event) { payload, _ ->
            aggregate.bracketsGenerated(payload)
        }.unwrap(event)
    }

    fun Category.bracketsGenerated(payload: BracketsGeneratedPayload): Category {
        val stages = payload.stages
        if (stages != null) {
            return this.copy(stages = stages)
        } else {
            throw CategoryNotFoundException("Fights are null or empty or category ID is empty.")
        }
    }

    override val eventType: EventType
        get() = EventType.BRACKETS_GENERATED
}