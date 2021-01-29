package compman.compsrv.service.processor.competitor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competitor
import compman.compsrv.config.COMPETITOR_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorRemovedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITOR_EVENT_HANDLERS)
class CompetitorRemoved(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Competitor>, ValidatedEventExecutor<Competitor>(mapper, validators) {
    override fun applyEvent(
            aggregate: Competitor,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competitor = executeValidated<CompetitorRemovedPayload, Competitor>(event) { _, _ ->
        rocksDBOperations.deleteCompetitor(aggregate.competitorDTO.id)
        aggregate
    }.unwrap(event)

    override val eventType: EventType
        get() = EventType.COMPETITOR_REMOVED
}