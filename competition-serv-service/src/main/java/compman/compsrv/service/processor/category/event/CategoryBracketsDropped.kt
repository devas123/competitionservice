package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class CategoryBracketsDropped(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
            aggregate: Category?,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Category? {
        val stages = aggregate?.stages
        stages?.values?.flatMap { it.fights }?.forEach {
            rocksDBOperations.deleteFight(it)
        }
        return aggregate?.copy(stages = emptyMap())
    }

    override val eventType: EventType
        get() = EventType.CATEGORY_BRACKETS_DROPPED
}