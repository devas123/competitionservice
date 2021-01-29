package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.aggregate.Competitor
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.AddCompetitorPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.model.events.payload.CompetitorRemovedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.DelegatingAggregateService
import compman.compsrv.service.processor.ISagaExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component

@Component
class RemoveCompetitor(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>,
    override val delegatingAggregateService: DelegatingAggregateService
) : ISagaExecutor,
    ValidatedCommandExecutor<AbstractAggregate>(mapper, validators) {

    override fun createSaga(
        dbOperations: DBOperations,
        command: CommandDTO
    ): Either<SagaExecutionError, SagaStep<List<EventDTO>>> {
        return createSaga<AddCompetitorPayload>(command) { payload, _ ->
            val competitor = dbOperations.getCompetitor(command.competitorId)
            applyEvent(
                competitor.right(),
                AbstractAggregateService.createEvent(
                    command,
                    EventType.COMPETITOR_REMOVED,
                    CompetitorRemovedPayload(command.competitorId)
                )

            ).andStep(
                applyEvent(
                    Unit.left(),
                    AbstractAggregateService.createEvent(
                        command,
                        EventType.CATEGORY_NUMBER_OF_COMPETITORS_DECREASED,
                        null
                    ).apply {
                        categoryId = command.categoryId
                    }
                ),
                AbstractAggregateService.createEvent(
                    command,
                    EventType.COMPETITOR_ADDED,
                    CompetitorAddedPayload(payload.competitor)
                )
            )
        }
    }


    override val commandType: CommandType
        get() = CommandType.REMOVE_COMPETITOR_COMMAND
}