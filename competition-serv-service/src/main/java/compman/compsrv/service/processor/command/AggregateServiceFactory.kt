package compman.compsrv.service.processor.command

import compman.compsrv.aggregate.*
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.DBOperations
import org.springframework.stereotype.Component

@Component
class AggregateServiceFactory(private val categoryAggregateService: CategoryAggregateService,
                              private val competitionAggregateService: CompetitionAggregateService,
                              private val competitorAggregateService: CompetitorAggregateService) {
    fun getAggregateService(event: EventDTO): AbstractAggregateService<out AbstractAggregate> =
        when(AggregateTypeDecider.getEventAggregateType(event.type)) {
            AggregateType.CATEGORY -> categoryAggregateService
            AggregateType.COMPETITION -> competitionAggregateService
            AggregateType.COMPETITOR -> competitorAggregateService
            else -> throw IllegalArgumentException("No aggregator for SAGA events")
        }
    fun getAggregateService(command: CommandDTO): AbstractAggregateService<*> =
        when(AggregateTypeDecider.getCommandAggregateType(command.type)) {
            AggregateType.CATEGORY -> categoryAggregateService
            AggregateType.COMPETITION -> competitionAggregateService
            AggregateType.COMPETITOR -> competitorAggregateService
            else -> throw IllegalArgumentException("No aggregator for SAGA events")
        }

    fun applyEvent(aggregate: AbstractAggregate, event: EventDTO, dbOperations: DBOperations): AbstractAggregate {
        return when(aggregate) {
            is Category -> categoryAggregateService.applyEvent(aggregate, event, dbOperations)
            is Competition -> competitionAggregateService.applyEvent(aggregate, event, dbOperations)
            is Competitor -> competitorAggregateService.applyEvent(aggregate, event, dbOperations)
            else -> throw IllegalArgumentException("No aggregator for SAGA events")
        }
    }
    fun applyEvents(aggregate: AbstractAggregate, events: List<EventDTO>, dbOperations: DBOperations): AbstractAggregate {
        return when(aggregate) {
            is Category -> categoryAggregateService.applyEvents(aggregate, events, dbOperations)
            is Competition -> competitionAggregateService.applyEvents(aggregate, events, dbOperations)
            is Competitor -> competitorAggregateService.applyEvents(aggregate, events, dbOperations)
            else -> throw IllegalArgumentException("No aggregator for SAGA events")
        }
    }
}