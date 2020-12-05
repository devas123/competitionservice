package compman.compsrv.service.processor.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competitor
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.commands.payload.UpdateCompetitorPayload
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorRemovedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component


@Component
class CompetitorAggregateService constructor(mapper: ObjectMapper,
                                             validators: List<PayloadValidator>) : AbstractAggregateService<Competitor>(mapper, validators) {

    private val doAddCompetitor: CommandExecutor<Competitor> = { c, _, command ->
        val competitor = mapper.convertValue(command.payload, CompetitorDTO::class.java) //TODO: increment category's number of competitors too.
        c to c.process(competitor, command, this::createEvent)
    }

    private val doRemoveCompetitor: CommandExecutor<Competitor> = { competitor, rocksDb, command ->
        if (rocksDb.fightsCount(competitor.competitorDTO.categories.orEmpty().toList(), command.competitorId) <= 0) {
            competitor to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, CompetitorRemovedPayload(command.competitorId)))
        } else {
            throw IllegalStateException("There are already fights generated for the competitor. Please remove the fights first.")
        }
    }

    private val doUpdateCompetitor: CommandExecutor<Competitor> = { competitor, _, command ->
        executeValidated(command, UpdateCompetitorPayload::class.java) { payload, com ->
            competitor to competitor.process(payload, com, this::createEvent)
        }.unwrap(command)
    }

    private val doChangeCompetitorCategory: CommandExecutor<Competitor> = { competitor, _, command -> //TODO: update number of competitors in categories
        executeValidated(command, ChangeCompetitorCategoryPayload::class.java) { payload, com ->
            competitor to competitor.process(payload, com, this::createEvent)
        }.unwrap(command)
    }




    override val commandsToHandlers: Map<CommandType, CommandExecutor<Competitor>> = mapOf(
            CommandType.ADD_COMPETITOR_COMMAND to doAddCompetitor,
            CommandType.REMOVE_COMPETITOR_COMMAND to doRemoveCompetitor,
            CommandType.UPDATE_COMPETITOR_COMMAND to doUpdateCompetitor,
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND to doChangeCompetitorCategory)


    override fun getAggregate(command: CommandDTO, rocksDBOperations: DBOperations): Competitor {
        return when (command.type) {
            CommandType.ADD_COMPETITOR_COMMAND -> {
                Competitor(CompetitorDTO())
            }
            else -> {
                rocksDBOperations.getCompetitor(command.competitorId)
            }
        }
    }

    override fun getAggregate(event: EventDTO, rocksDBOperations: DBOperations): Competitor = rocksDBOperations.getCompetitor(event.competitorId, true)
    override val eventsToPayloads: Map<EventType, Class<out Payload>>
        get() = TODO("Not yet implemented")

    override fun Payload.accept(aggregate: Competitor, event: EventDTO): Competitor {
        TODO("Not yet implemented")
    }

    override fun saveAggregate(aggregate: Competitor, rocksDBOperations: DBOperations): Competitor {
        TODO("Not yet implemented")
    }

}