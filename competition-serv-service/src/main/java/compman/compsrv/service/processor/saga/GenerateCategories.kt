package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.aggregate.Category
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.GenerateCategoriesFromRestrictionsPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CategoryAddedPayload
import compman.compsrv.model.events.payload.CompetitionCategoriesPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.CategoryGeneratorService
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.DelegatingAggregateService
import compman.compsrv.service.processor.ISagaExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component

@Component
class GenerateCategories(
    mapper: ObjectMapper, validators: List<PayloadValidator>,
    private val categoryGeneratorService: CategoryGeneratorService,
    override val delegatingAggregateService: DelegatingAggregateService
) : ISagaExecutor,
    ValidatedCommandExecutor<AbstractAggregate>(mapper, validators) {
    override fun createSaga(
        dbOperations: DBOperations,
        command: CommandDTO
    ): Either<SagaExecutionError, SagaStep<List<EventDTO>>> =
        createSaga<GenerateCategoriesFromRestrictionsPayload>(command) { payload, com ->
            val categories = categoryGeneratorService.generateCategories(com.competitionId, payload)

            categories.map {
                applyEvent(
                    Category(it.id, it).right(),
                    AbstractAggregateService.createEvent(com, EventType.CATEGORY_ADDED, CategoryAddedPayload(it))
                        .apply { categoryId = it.id }) + (
                        applyEvent(
                            Unit.left(), AbstractAggregateService.createEvent(
                                com, EventType.COMPETITION_CATEGORIES_ADDED,
                                CompetitionCategoriesPayload(arrayOf(it.id))
                            )
                        ) to AbstractAggregateService.createEvent(com, EventType.CATEGORY_DELETED, null)
                            .apply { categoryId = it.id }
                        )
            }.reduce { acc, f ->
                acc + f
            }
        }


    override val commandType: CommandType
        get() = CommandType.GENERATE_CATEGORIES_COMMAND
}