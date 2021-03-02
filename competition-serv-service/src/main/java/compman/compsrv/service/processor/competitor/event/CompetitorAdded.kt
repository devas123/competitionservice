package compman.compsrv.service.processor.competitor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competitor
import compman.compsrv.config.COMPETITOR_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITOR_EVENT_HANDLERS)
class CompetitorAdded(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : IEventHandler<Competitor>, ValidatedEventExecutor<Competitor>(mapper, validators) {
    companion object {
        private val log = LoggerFactory.getLogger(CompetitorAdded::class.java)
    }

    override fun applyEvent(
        aggregate: Competitor?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Competitor? = aggregate?.let {
        executeValidated<CompetitorAddedPayload, Competitor>(event) { payload, _ ->
            val competitor = payload.fighter
            if (competitor != null && !competitor.id.isNullOrBlank() && !competitor.categories.isNullOrEmpty()) {
                log.info("Adding competitor: ${competitor.id} to competition ${event.competitionId} and category ${competitor.categories}")
                aggregate.copy(competitorDTO = competitor)
            } else {
                throw EventApplyingException("No competitor in the event payload: $event", event)
            }
        }.unwrap(event)
    } ?: error(Constants.CANNOT_CREATE_COMPETITOR)

    override val eventType: EventType
        get() = EventType.COMPETITOR_ADDED
}