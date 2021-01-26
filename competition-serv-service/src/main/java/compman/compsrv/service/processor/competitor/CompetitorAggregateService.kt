package compman.compsrv.service.processor.competitor

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competitor
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.AddCompetitorPayload
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.commands.payload.UpdateCompetitorPayload
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.CommandExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component


@Component
class CompetitorAggregateService : AbstractAggregateService<Competitor>() {

    private val doAddCompetitor: CommandExecutor<Competitor> = { c, _, command ->
        executeValidated<AddCompetitorPayload>(command) { payload, _ ->
            c to c.process(payload.competitor, command, Companion::createEvent)
        }.unwrap(command)
    }

    private val doRemoveCompetitor: CommandExecutor<Competitor> = { competitor, rocksDb, command ->
        if (rocksDb.fightsCount(competitor.competitorDTO.categories.orEmpty().toList(), command.competitorId) <= 0) {
            competitor to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, CompetitorRemovedPayload(command.competitorId)))
        } else {
            throw IllegalStateException("There are already fights generated for the competitor. Please remove the fights first.")
        }
    }

    private val doUpdateCompetitor: CommandExecutor<Competitor> = { competitor, _, command ->
        executeValidated<UpdateCompetitorPayload>(command) { payload, com ->
            competitor to competitor.process(payload, com, Companion::createEvent)
        }.unwrap(command)
    }

    private val doChangeCompetitorCategory: CommandExecutor<Competitor> = { competitor, _, command -> //TODO: update number of competitors in categories
        executeValidated<ChangeCompetitorCategoryPayload>(command) { payload, com ->
            competitor to competitor.process(payload, com, Companion::createEvent)
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

    override fun Payload.accept(aggregate: Competitor, event: EventDTO): Competitor {
        TODO("Not yet implemented")
    }

    override fun saveAggregate(aggregate: Competitor, rocksDBOperations: DBOperations): Competitor {
        TODO("Not yet implemented")
    }

}