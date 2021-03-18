package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
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
class FightsStartTimeCleaned(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
        aggregate: Category?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Category? = aggregate?.fightStartTimeCleaned(rocksDBOperations) ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.fightStartTimeCleaned(rocksDBOperations: DBOperations): Category {
        stages.values.flatMap { it.fights }.forEach {
            val f = rocksDBOperations.getFight(it)
            f.invalid = false
            f.mat = null
            f.period = null
            f.startTime = null
            f.numberOnMat = null
            f.scheduleEntryId = null
            rocksDBOperations.putFight(f)
        }
        return this
    }

    override val eventType: EventType
        get() = EventType.FIGHTS_START_TIME_CLEANED
}