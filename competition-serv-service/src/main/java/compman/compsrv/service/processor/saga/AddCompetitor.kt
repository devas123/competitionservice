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
class AddCompetitor(
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
            val competitorId = command.competitorId ?: payload.competitor.id ?: IDGenerator.hashString("${command.competitionId}/${command.categoryId}/${payload.competitor.email}")
            val competitor = Competitor(payload.competitor.setId(competitorId))
            applyEvent(
                competitor.right(),
                AbstractAggregateService.createEvent(
                    command,
                    EventType.COMPETITOR_ADDED,
                    CompetitorAddedPayload(payload.competitor.setId(competitorId))
                )
            ).andStep(
                applyEvent(
                    Unit.left(),
                    AbstractAggregateService.createEvent(
                        command,
                        EventType.CATEGORY_NUMBER_OF_COMPETITORS_INCREASED,
                        null
                    ).apply {
                        categoryId = command.categoryId
                    }
                ),
                AbstractAggregateService.createEvent(
                    command,
                    EventType.COMPETITOR_REMOVED,
                    CompetitorRemovedPayload(competitorId)
                )
            )
        }
    }


    override val commandType: CommandType
        get() = CommandType.ADD_COMPETITOR_COMMAND
}