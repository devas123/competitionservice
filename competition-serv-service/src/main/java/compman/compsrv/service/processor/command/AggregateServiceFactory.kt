package compman.compsrv.service.processor.command

import compman.compsrv.aggregate.AggregateType
import compman.compsrv.aggregate.AggregateTypeDecider
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import org.springframework.stereotype.Component

@Component
class AggregateServiceFactory(private val categoryAggregateService: CategoryAggregateService,
                              private val competitionAggregateService: CompetitionAggregateService,
                              private val competitorAggregateService: CompetitorAggregateService) {
    fun getAggregateService(event: EventDTO): AbstractAggregateService<*> =
        when(AggregateTypeDecider.getEventAggregateType(event.type)) {
            AggregateType.CATEGORY -> categoryAggregateService
            AggregateType.COMPETITION -> competitionAggregateService
            AggregateType.COMPETITOR -> competitorAggregateService
            AggregateType.SAGA -> throw IllegalArgumentException("No aggregator for SAGA events")
        }
    fun getAggregateService(command: CommandDTO): AbstractAggregateService<*> =
        when(AggregateTypeDecider.getCommandAggregateType(command.type)) {
            AggregateType.CATEGORY -> categoryAggregateService
            AggregateType.COMPETITION -> competitionAggregateService
            AggregateType.COMPETITOR -> competitorAggregateService
            AggregateType.SAGA -> throw IllegalArgumentException("No aggregator for SAGA events")
        }
}