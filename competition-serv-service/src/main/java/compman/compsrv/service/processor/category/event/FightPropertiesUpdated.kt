package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.FightPropertiesUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class FightPropertiesUpdated(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
        aggregate: Category?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Category? = aggregate?.let {
        executeValidated<FightPropertiesUpdatedPayload, Category>(event) { payload, _ ->
            aggregate.fightPropertiesUpdated(payload)
        }.unwrap(event)
    } ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.fightPropertiesUpdated(
        payload: FightPropertiesUpdatedPayload
    ): Category {
        payload.updates.forEach { upd ->
            fightsMapIndices[upd.fightId]?.let { ind ->
                fights[ind] = fights[ind].apply {
                    upd.mat?.let { mat = it }
                    upd.numberOnMat?.let { numberOnMat = it }
                    upd.startTime?.let { startTime = it }
                }
            }
        }

        return this
    }

    override val eventType: EventType
        get() = EventType.FIGHT_PROPERTIES_UPDATED
}