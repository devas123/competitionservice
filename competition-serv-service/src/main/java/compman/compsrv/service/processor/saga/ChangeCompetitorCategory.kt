package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.*
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.copy
import org.springframework.stereotype.Component

@Component
class ChangeCompetitorCategory(
    mapper: ObjectMapper, validators: List<PayloadValidator>,
    private val aggregateServiceFactory: AggregateServiceFactory
) : ISagaExecutor,
    ValidatedCommandExecutor<AbstractAggregate>(mapper, validators) {
    override fun executeSaga(
        dbOperations: DBOperations,
        command: CommandDTO
    ): Either<SagaExecutionError, List<EventDTO>> =
        executeValidatedMultiple<ChangeCompetitorCategoryPayload>(command) { payload, _ ->
            val competitor = dbOperations.getCompetitor(payload.fighterId, true)
            val categories = competitor.competitorDTO.categories ?: emptyArray()
            val sagas = applyEvent(competitor.right(),
                AbstractAggregateService.createEvent(command,
                    EventType.COMPETITOR_UPDATED,
                    CompetitorUpdatedPayload(competitor.competitorDTO.copy(categories = categories.filter { it != payload.oldCategoryId && it != payload.newCategoryId }
                        .toTypedArray() + payload.newCategoryId))
                )
            ).andStep(
                applyEvent(
                    Unit.left(),
                    AbstractAggregateService.createEvent(command,
                        EventType.CATEGORY_NUMBER_OF_COMPETITORS_INCREASED,
                        null).apply {
                        categoryId = payload.newCategoryId
                    }
                ).andStep(
                    applyEvent(
                        Unit.left(),
                        AbstractAggregateService.createEvent(command,
                            EventType.CATEGORY_NUMBER_OF_COMPETITORS_DECREASED,
                            null).apply {
                            categoryId = payload.oldCategoryId
                        }
                    ),
                    AbstractAggregateService.createEvent(command,
                        EventType.CATEGORY_NUMBER_OF_COMPETITORS_DECREASED,
                        null).apply {
                        categoryId = payload.newCategoryId
                    }
                ),
                AbstractAggregateService.createEvent(command,
                    EventType.COMPETITOR_UPDATED,
                    CompetitorUpdatedPayload(competitor.competitorDTO)
                )
            )
            sagas.accumulate(dbOperations, aggregateServiceFactory).doRun()
        }


    override val commandType: CommandType
        get() = CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND
}
