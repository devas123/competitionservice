package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.aggregate.Category
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.GenerateCategoriesFromRestrictionsPayload
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CategoryAddedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.CategoryGeneratorService
import compman.compsrv.service.processor.*
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component

@Component
class CreateFakeCompetitors(
    mapper: ObjectMapper, validators: List<PayloadValidator>,
    private val categoryGeneratorService: CategoryGeneratorService,
    private val aggregateServiceFactory: AggregateServiceFactory
) : ISagaExecutor,
    ValidatedCommandExecutor<AbstractAggregate>(mapper, validators) {
    override fun executeSaga(
        dbOperations: DBOperations,
        command: CommandDTO
    ): Either<SagaExecutionError, List<AggregateWithEvents<AbstractAggregate>>> =
        executeValidatedMultiple<GenerateCategoriesFromRestrictionsPayload>(command) { payload, com ->
            val categories = payload.idTrees.flatMap { idTree ->
                val restrNamesOrder = payload.restrictionNames.mapIndexed { index, s -> s to index }.toMap()
                categoryGeneratorService.generateCategoriesFromRestrictions(
                    com.competitionId,
                    payload.restrictions,
                    idTree,
                    restrNamesOrder
                )
            }
            val sagas = categories.map {
                applyEvent(
                    Category(it.id, it).right(),
                    AbstractAggregateService.createEvent(com, EventType.CATEGORY_ADDED, CategoryAddedPayload(it))
                        .apply { categoryId = it.id })
            }.reduce { acc, f -> acc.andStep(f) }
            sagas.accumulate(dbOperations, aggregateServiceFactory).doRun()
        }


    override val commandType: CommandType
        get() = CommandType.GENERATE_CATEGORIES_COMMAND
}