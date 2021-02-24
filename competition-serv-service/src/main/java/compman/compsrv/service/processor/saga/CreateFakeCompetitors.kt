package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competitor
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.CreateFakeCompetitorsPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.model.events.payload.CompetitorRemovedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.DelegatingAggregateService
import compman.compsrv.service.processor.ISagaExecutor
import org.springframework.stereotype.Component

@Component
class CreateFakeCompetitors(
    private val mapper: ObjectMapper,
    override val delegatingAggregateService: DelegatingAggregateService
) : ISagaExecutor {
    override fun createSaga(
        dbOperations: DBOperations,
        command: CommandDTO
    ): Either<Nothing, SagaStep<List<EventDTO>>> {
        val payload = mapper.convertValue(command.payload, CreateFakeCompetitorsPayload::class.java)
        val numberOfCompetitors = payload?.numberOfCompetitors ?: 50
        val numberOfAcademies = payload?.numberOfAcademies ?: 30
        val fakeCompetitors = FightsService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, command.categoryId, command.competitionId!!)
        val sagas = fakeCompetitors.map {
            applyEvent(Competitor(it).right(), AbstractAggregateService.createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(it))) +
                    (applyEvent(Unit.left(), AbstractAggregateService.createEvent(command, EventType.CATEGORY_NUMBER_OF_COMPETITORS_INCREASED, null)) to
                            AbstractAggregateService.createEvent(command, EventType.COMPETITOR_REMOVED, CompetitorRemovedPayload(it.id)))
        }.reduce { a, b -> a + b }
        return sagas.right()
    }

    override val commandType: CommandType
        get() = CommandType.CREATE_FAKE_COMPETITORS_COMMAND
}