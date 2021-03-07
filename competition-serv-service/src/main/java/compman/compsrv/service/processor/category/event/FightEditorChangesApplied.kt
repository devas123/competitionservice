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
            aggregate.applyFightEditorChanges(payload, rocksDBOperations)
        }.unwrap(event)
    } ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.applyFightEditorChanges(
        payload: FightEditorChangesAppliedPayload,
        dbOperations: DBOperations
    ): Category {
        val removals = payload.removedFighids.orEmpty().toSet()
        val updates = payload.updates.orEmpty()
        val newFights = payload.newFights.orEmpty()
        removals.forEach { dbOperations.getFight(it) }
        newFights.forEach { dbOperations.putFight(it) }
        updates.forEach { f -> dbOperations.putFight(f) }
        return this
    }

    override val eventType: EventType
        get() = EventType.FIGHTS_EDITOR_CHANGE_APPLIED
}