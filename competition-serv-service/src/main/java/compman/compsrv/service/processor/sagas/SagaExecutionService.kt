package compman.compsrv.service.processor.sagas

import arrow.core.Either
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.aggregate.Category
import compman.compsrv.aggregate.Competitor
import compman.compsrv.errors.CommandProcessingError
import compman.compsrv.errors.show
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.CreateFakeCompetitorsPayload
import compman.compsrv.model.commands.payload.GenerateCategoriesFromRestrictionsPayload
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CategoryAddedPayload
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.model.exceptions.CommandProcessingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.CategoryGeneratorService
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.processor.command.AggregateServiceFactory
import compman.compsrv.service.processor.command.AggregateWithEvents
import compman.compsrv.service.processor.command.ValidatedExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component

@Component
class SagaExecutionService(private val aggregateServiceFactory: AggregateServiceFactory, private val categoryGeneratorService: CategoryGeneratorService, mapper: ObjectMapper, validators: List<PayloadValidator>) : ValidatedExecutor<AbstractAggregate>(mapper, validators) {
    fun executeSaga(c: CommandDTO, rocksDBOperations: DBOperations): Either<CommandProcessingError, List<AggregateWithEvents<AbstractAggregate>>> {
        return when(c.type) {
         CommandType.GENERATE_CATEGORIES_COMMAND -> {
             executeValidatedMultiple(c, GenerateCategoriesFromRestrictionsPayload::class.java) { payload, com ->
                 val categories = payload.idTrees.flatMap { idTree ->
                     val restrNamesOrder = payload.restrictionNames.mapIndexed { index, s -> s to index }.toMap()
                     categoryGeneratorService.generateCategoriesFromRestrictions(com.competitionId, payload.restrictions, idTree, restrNamesOrder)
                 }
                 val sagas = categories.map { applyEvent(Category(it.id, it).right(), createEvent(com, EventType.CATEGORY_ADDED, CategoryAddedPayload(it))
                     .setCategoryId(it.id)) }.reduce { acc, f -> acc.andStep(f) }
                 sagas.accumulate(rocksDBOperations, aggregateServiceFactory).doRun()
                     .fold({throw CommandProcessingException("Errors during saga execution: ${it.show()}", c)}, {it})
             }
         }
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND -> {
                val payload = mapper.convertValue(c.payload, CreateFakeCompetitorsPayload::class.java)
                val numberOfCompetitors = payload?.numberOfCompetitors ?: 50
                val numberOfAcademies = payload?.numberOfAcademies ?: 30
                val fakeCompetitors = FightsService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, c.categoryId, c.competitionId!!)
                val sagas = fakeCompetitors.map {
                    applyEvent(Competitor(it).right(), createEvent(c, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(it)) )
                }.reduce { a, b -> a.andStep(b) }
                    sagas.accumulate(rocksDBOperations, aggregateServiceFactory).doRun().mapLeft { CommandProcessingError.GenericError("Errors during saga execution: ${it.show()}") }
            }
            else -> {
             val saga = processCommand(c).execute()
             saga.accumulate(rocksDBOperations, aggregateServiceFactory).doRun().mapLeft { CommandProcessingError.GenericError("Errors during saga execution: ${it.show()}") }
         }
        }
    }
}