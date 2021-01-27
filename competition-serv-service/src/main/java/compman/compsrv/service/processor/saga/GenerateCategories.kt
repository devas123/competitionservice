package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.aggregate.Competitor
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.CreateFakeCompetitorsPayload
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.processor.*
import org.springframework.stereotype.Component

@Component
class GenerateCategories(
    private val mapper: ObjectMapper,
    private val aggregateServiceFactory: AggregateServiceFactory
) : ISagaExecutor {
    override fun executeSaga(
        dbOperations: DBOperations,
        command: CommandDTO
    ): Either<SagaExecutionError, List<AggregateWithEvents<AbstractAggregate>>> {
        val payload = mapper.convertValue(command.payload, CreateFakeCompetitorsPayload::class.java)
        val numberOfCompetitors = payload?.numberOfCompetitors ?: 50
        val numberOfAcademies = payload?.numberOfAcademies ?: 30
        val fakeCompetitors = FightsService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, command.categoryId, command.competitionId!!)
        val sagas = fakeCompetitors.map {
            applyEvent(Competitor(it).right(), AbstractAggregateService.createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(it)) )
        }.reduce { a, b -> a.andStep(b) }
        return sagas.accumulate(dbOperations, aggregateServiceFactory).doRun()
    }

    override val commandType: CommandType
        get() = CommandType.GENERATE_CATEGORIES_COMMAND
}