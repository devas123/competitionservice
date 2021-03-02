package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import compman.compsrv.errors.SagaExecutionError
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.DelegatingAggregateService
import compman.compsrv.service.processor.ISagaExecutor
import org.springframework.stereotype.Component

@Component
class DropSchedule(override val delegatingAggregateService: DelegatingAggregateService) : ISagaExecutor {

    override fun createSaga(
        dbOperations: DBOperations,
        command: CommandDTO
    ): Either<SagaExecutionError, SagaStep<List<EventDTO>>> {
        val entity = dbOperations.getCompetition(command.competitionId)
        return if (entity.properties.schedulePublished != true) {
            val fightsCleanedEvents = entity.categories.map { cat ->
                applyEvent(
                    Unit.left(),
                    AbstractAggregateService.createEvent(command, EventType.FIGHTS_START_TIME_CLEANED, null).apply {
                        categoryId = cat
                    })
            }.reduce { a, b -> a + b }
            (applyEvent(
                entity.right(),
                AbstractAggregateService.createEvent(command, EventType.SCHEDULE_DROPPED, command.payload)
            ) + fightsCleanedEvents).right()
        } else {
            SagaExecutionError.GenericError("Schedule already published").left()
        }
    }

    override val commandType: CommandType
        get() = CommandType.DROP_SCHEDULE_COMMAND
}

