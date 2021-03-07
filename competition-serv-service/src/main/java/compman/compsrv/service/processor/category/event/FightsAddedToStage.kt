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
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class FightsAddedToStage(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
        aggregate: Category?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Category? = aggregate?.let {
        executeValidated<FightsAddedToStagePayload, Category>(event) { payload, _ ->
            aggregate.fightsAddedToStage(payload, rocksDBOperations)
        }.unwrap(event)
    } ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.fightsAddedToStage(payload: FightsAddedToStagePayload, rocksDBOperations: DBOperations): Category {
        val stage = stages.getValue(payload.stageId)
        val fm = payload.fights.filter { !stage.fights.contains(it.id) }
        fm.forEach { rocksDBOperations.putFight(it) }
        return this.copy(stages = (stages - payload.stageId) + (stage.id to stage.copy(fights = stage.fights + fm.map { it.id })))
    }

    override val eventType: EventType
        get() = EventType.FIGHTS_ADDED_TO_STAGE
}