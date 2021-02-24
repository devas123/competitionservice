package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.FightEditorChangesAppliedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.applyConditionalUpdate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class FightEditorChangesApplied(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
        aggregate: Category?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Category? = aggregate?.let {
        return executeValidated<FightEditorChangesAppliedPayload, Category>(event) { payload, _ ->
            aggregate.applyFightEditorChanges(payload)
        }.unwrap(event)
    } ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.applyFightEditorChanges(payload: FightEditorChangesAppliedPayload): Category {
        val removals = payload.removedFighids.orEmpty().toSet()
        val updates = payload.updates.orEmpty().map { it.id to it }.toMap()
        val newFights = payload.newFights.orEmpty()
        val fights = this.fights.filter { !removals.contains(it.id) } + newFights.toList()
        fights.applyConditionalUpdate({ updates.containsKey(it.id) }, { updates.getValue(it.id) })
        return this.copy(fights = fights.toTypedArray())
    }

    override val eventType: EventType
        get() = EventType.FIGHTS_EDITOR_CHANGE_APPLIED
}