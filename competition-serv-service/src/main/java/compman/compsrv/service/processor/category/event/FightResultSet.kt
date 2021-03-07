package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.competition.FightStatus
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
class FightResultSet(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
            aggregate: Category?,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Category? = aggregate?.let {
        executeValidated<SetFightResultPayload, Category>(event) { payload, _ ->
            aggregate.fightResultSet(payload, rocksDBOperations)
        }.unwrap(event)
    }  ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.fightResultSet(payload: SetFightResultPayload, rocksDBOperations: DBOperations): Category {
        rocksDBOperations.getFight(payload.fightId).let { f ->
            f.scores = payload.scores
            f.status = FightStatus.FINISHED
            f.fightResult = payload.fightResult
            rocksDBOperations.putFight(f)
        }
        return this
    }

    override val eventType: EventType
        get() = EventType.DASHBOARD_FIGHT_RESULT_SET
}