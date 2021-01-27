package compman.compsrv.service.processor.competitor.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competitor
import compman.compsrv.config.COMPETITOR_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.commands.payload.UpdateCompetitorPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITOR_COMMAND_EXECUTORS)
class ChangeCompetitorCategory(mapper: ObjectMapper, validators: List<PayloadValidator>) : ICommandExecutor<Competitor>, ValidatedCommandExecutor<Competitor>(mapper, validators) {
    override fun execute(
            entity: Competitor,
            dbOperations: DBOperations,
            command: CommandDTO
    ): AggregateWithEvents<Competitor> = executeValidated<UpdateCompetitorPayload>(command) { payload, com ->
        entity to entity.process(payload, com, AbstractAggregateService.Companion::createEvent)
    }.unwrap(command)

    fun Competitor.process(payload: UpdateCompetitorPayload, command: CommandDTO, createEvent: (command: CommandDTO, eventType: EventType, payload: Payload?) -> EventDTO): List<EventDTO> {
        return listOf(createEvent(command, EventType.COMPETITOR_UPDATED, CompetitorUpdatedPayload(payload.competitor)))
    }


    override val commandType: CommandType
        get() = CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND
}