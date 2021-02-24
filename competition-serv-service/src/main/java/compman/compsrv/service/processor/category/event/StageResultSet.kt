package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.StageResultSetPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class StageResultSet(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
        aggregate: Category?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Category? = aggregate?.let {
        return executeValidated<StageResultSetPayload, Category>(event) { payload, _ ->
            aggregate.stageResultSet(payload)
        }.unwrap(event)
    } ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.stageResultSet(payload: StageResultSetPayload): Category {
        stages.find { it.id == payload.stageId }?.let {
            it.stageStatus = StageStatus.FINISHED
            it.stageResultDescriptor.competitorResults = payload.results
        }
        return this
    }

    override val eventType: EventType
        get() = EventType.DASHBOARD_STAGE_RESULT_SET
}