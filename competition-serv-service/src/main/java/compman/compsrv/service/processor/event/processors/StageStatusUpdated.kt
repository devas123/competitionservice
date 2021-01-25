package compman.compsrv.service.processor.event.processors

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.StageStatusUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.command.ValidatedExecutor
import compman.compsrv.service.processor.event.IEventProcessor
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component

@Component
class StageStatusUpdated(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
): IEventProcessor<Category>, ValidatedExecutor<Category>(mapper, validators) {
    override fun applyEvent(
        aggregate: Category,
        event: EventDTO,
        rocksDBOperations: DBOperations,
        save: Boolean
    ): Category {
        return executeValidated<StageStatusUpdatedPayload, Category>(event) { payload, _ ->
            aggregate.stageStatusUpdated(payload)
        }.unwrap(event)
    }

    override val eventType: EventType
        get() = EventType.STAGE_STATUS_UPDATED
}