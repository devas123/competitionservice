package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class CategoryDeleted(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
        aggregate: Category?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Category? {
        return if (!event.categoryId.isNullOrBlank()) {
            aggregate?.also {
                val competition = rocksDBOperations.getCompetition(event.competitionId, true)
                rocksDBOperations.putCompetition(competition.copy(categories = competition.categories.filter { it != event.categoryId }
                    .toTypedArray(), registrationInfo = competition.registrationInfo.also { r ->
                    r.registrationGroups?.forEach { rg ->
                        rg.categories = rg.categories.filter { it != event.categoryId }.toTypedArray()
                    }
                    Unit
                }))
                rocksDBOperations.deleteCategory(event.categoryId, event.competitionId)
            }
        } else {
            throw EventApplyingException("Category ID is null.", event)
        }
    }

    override val eventType: EventType
        get() = EventType.CATEGORY_DELETED
}