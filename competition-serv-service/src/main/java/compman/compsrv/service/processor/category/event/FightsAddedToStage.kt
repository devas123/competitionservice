package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.FightsAddedToStagePayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.applyConditionalUpdateArray
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class FightsAddedToStage(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
            aggregate: Category,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Category {
        return executeValidated<FightsAddedToStagePayload, Category>(event) { payload, _ ->
            aggregate.fightsAddedToStage(payload)
        }.unwrap(event)
    }

    fun Category.fightsAddedToStage(payload: FightsAddedToStagePayload): Category {
        val fm = payload.fights.map { it.id to it }.toMap()
        return this.copy(fights = fights.applyConditionalUpdateArray({ it.stageId == payload.stageId && fm.containsKey(it.id) }, { fm.getValue(it.id) }) + payload.fights.filter { !fightsMap.containsKey(it.id) })
    }

    override val eventType: EventType
        get() = EventType.FIGHTS_ADDED_TO_STAGE
}