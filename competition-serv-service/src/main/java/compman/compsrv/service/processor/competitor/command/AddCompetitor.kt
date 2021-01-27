package compman.compsrv.service.processor.competitor.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competitor
import compman.compsrv.config.COMPETITOR_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.AddCompetitorPayload
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITOR_COMMAND_EXECUTORS)
class AddCompetitor(mapper: ObjectMapper, validators: List<PayloadValidator>) : ICommandExecutor<Competitor>, ValidatedCommandExecutor<Competitor>(mapper, validators) {
    override fun execute(
            entity: Competitor,
            dbOperations: DBOperations,
            command: CommandDTO
    ): AggregateWithEvents<Competitor> = executeValidated<AddCompetitorPayload>(command) { payload, _ ->
        entity to entity.process(payload.competitor, command, AbstractAggregateService.Companion::createEvent)
    }.unwrap(command)

    fun Competitor.process(payload: CompetitorDTO, command: CommandDTO, createEvent: (command: CommandDTO, eventType: EventType, payload: Payload?) -> EventDTO): List<EventDTO> {
        val competitorId = IDGenerator.hashString("${command.competitionId}/${command.categoryId}/${payload.email}")
        return if (payload.categories?.contains(command.categoryId) == true) {
            listOf(createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(payload.setId(competitorId))))
        } else {
            throw IllegalArgumentException("Failed to get competitor from payload. Or competitor already exists")
        }
    }


    override val commandType: CommandType
        get() = CommandType.ADD_COMPETITOR_COMMAND
}