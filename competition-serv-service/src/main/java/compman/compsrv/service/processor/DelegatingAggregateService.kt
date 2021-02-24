package compman.compsrv.service.processor

import compman.compsrv.aggregate.*
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.category.CategoryAggregateService
import compman.compsrv.service.processor.competition.CompetitionAggregateService
import compman.compsrv.service.processor.competitor.CompetitorAggregateService
import org.springframework.stereotype.Component

@Component
class DelegatingAggregateService(
    private val categoryAggregateService: CategoryAggregateService,
    private val competitionAggregateService: CompetitionAggregateService,
    private val competitorAggregateService: CompetitorAggregateService
) {
    fun getAggregateService(event: EventDTO): AbstractAggregateService<out AbstractAggregate> =
        when (AggregateTypeDecider.getEventAggregateType(event.type)) {
            AggregateType.CATEGORY -> categoryAggregateService
            AggregateType.COMPETITION -> competitionAggregateService
            AggregateType.COMPETITOR -> competitorAggregateService
            else -> throw IllegalArgumentException("No aggregator for SAGA events")
        }

    fun getAggregateService(command: CommandDTO): AbstractAggregateService<*> =
        when (AggregateTypeDecider.getCommandAggregateType(command.type)) {
            AggregateType.CATEGORY -> categoryAggregateService
            AggregateType.COMPETITION -> competitionAggregateService
            AggregateType.COMPETITOR -> competitorAggregateService
            else -> throw IllegalArgumentException("No aggregator for SAGA events")
        }

    fun getAggregate(event: EventDTO, dbOperations: DBOperations): AbstractAggregate? =
        getAggregateService(event).getAggregate(event, dbOperations)

    fun <T : AbstractAggregate> applyEvent(
        aggregate: T?,
        event: EventDTO,
        dbOperations: DBOperations
    ): AbstractAggregate? {
        return when (aggregate) {
            is Category -> categoryAggregateService.applyEvent(aggregate, event, dbOperations)
            is Competition -> competitionAggregateService.applyEvent(aggregate, event, dbOperations)
            is Competitor -> competitorAggregateService.applyEvent(aggregate, event, dbOperations)
            else -> throw IllegalArgumentException("No aggregate service for aggregate: $aggregate")
        }
    }

    fun applyEvents(
        aggregate: AbstractAggregate?,
        events: List<EventDTO>,
        dbOperations: DBOperations
    ): AbstractAggregate? = aggregate?.let {
        return when (aggregate) {
            is Category -> categoryAggregateService.applyEvents(aggregate, events, dbOperations)
            is Competition -> competitionAggregateService.applyEvents(aggregate, events, dbOperations)
            is Competitor -> competitorAggregateService.applyEvents(aggregate, events, dbOperations)
            else -> throw IllegalArgumentException("No aggregate service for SAGA events")
        }
    }
}