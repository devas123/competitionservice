package compman.compsrv.service.processor.competitor

import compman.compsrv.aggregate.Competitor
import compman.compsrv.config.COMPETITOR_COMMAND_EXECUTORS
import compman.compsrv.config.COMPETITOR_EVENT_HANDLERS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.CommandExecutor
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.IEventHandler
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component


@Component
class CompetitorAggregateService(
    @Qualifier(COMPETITOR_COMMAND_EXECUTORS)
    commandExecutors: List<ICommandExecutor<Competitor>>,
    @Qualifier(COMPETITOR_EVENT_HANDLERS)
    eventHandlers: List<IEventHandler<Competitor>>
) : AbstractAggregateService<Competitor>() {


//    private val doChangeCompetitorCategory: CommandExecutor<Competitor> = { competitor, _, command -> //TODO: update number of competitors in categories
//        executeValidated<ChangeCompetitorCategoryPayload>(command) { payload, com ->
//            competitor to competitor.process(payload, com, Companion::createEvent)
//        }.unwrap(command)
//    }


    override val commandsToHandlers: Map<CommandType, CommandExecutor<Competitor>> =
        commandExecutors.groupBy { it.commandType }.mapValues { it ->
            it.value.first()
        }.mapValues { e -> { cmp: Competitor?, ops: DBOperations, c: CommandDTO -> e.value.execute(cmp, ops, c) } }


    override fun getAggregate(command: CommandDTO, rocksDBOperations: DBOperations): Competitor? {
        return when (command.type) {
            CommandType.ADD_COMPETITOR_COMMAND -> {
                Competitor(CompetitorDTO())
            }
            else -> {
                kotlin.runCatching { rocksDBOperations.getCompetitor(command.competitorId) }.getOrLogAndNull()
            }
        }
    }

    override fun getAggregate(event: EventDTO, rocksDBOperations: DBOperations): Competitor? =
        when (event.type) {
            EventType.COMPETITOR_ADDED -> {
                Competitor(CompetitorDTO())
            }
            else -> {
                kotlin.runCatching { rocksDBOperations.getCompetitor(event.competitorId, true) }.getOrLogAndNull()
            }
        }

    override val eventsToProcessors
            : Map<EventType, IEventHandler<Competitor>> =
        eventHandlers.groupBy { it.eventType }.mapValues { e -> e.value.first() }

    override fun saveAggregate(
        aggregate: Competitor?, rocksDBOperations: DBOperations
    )
            : Competitor
    ? {
        return aggregate?.also { rocksDBOperations.putCompetitor(aggregate) }
    }

    override fun isAggregateDeleted(event: EventDTO)
            : Boolean = event.type == EventType.COMPETITOR_REMOVED

}