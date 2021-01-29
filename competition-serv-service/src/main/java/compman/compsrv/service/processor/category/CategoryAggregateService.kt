package compman.compsrv.service.processor.category

import compman.compsrv.aggregate.AggregateType
import compman.compsrv.aggregate.AggregateTypeDecider
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_COMMAND_EXECUTORS
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.GroupChangeType
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
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
class CategoryAggregateService constructor(
        @Qualifier(CATEGORY_COMMAND_EXECUTORS)
        commandExecutors: List<ICommandExecutor<Category>>,
        @Qualifier(CATEGORY_EVENT_HANDLERS)
        eventHandlers: List<IEventHandler<Category>>
) : AbstractAggregateService<Category>() {

    companion object {
        val changePriority = GroupChangeType.values().associate {
            it to when (it) {
                GroupChangeType.REMOVE -> 0
                GroupChangeType.ADD -> 1
                else -> Int.MAX_VALUE
            }
        }.withDefault { Int.MAX_VALUE }
    }


    private val commandsToExecutors = commandExecutors.groupBy { it.commandType }.mapValues { it.value.first() }


    override val commandsToHandlers: Map<CommandType, CommandExecutor<Category>> = commandsToExecutors.mapValues { e -> { cat: Category, ops: DBOperations, c: CommandDTO -> e.value.execute(cat, ops, c) } }

    override fun getAggregate(command: CommandDTO, rocksDBOperations: DBOperations): Category {
        return when (command.type) {
            CommandType.ADD_CATEGORY_COMMAND, CommandType.GENERATE_CATEGORIES_COMMAND -> {
                Category(command.categoryId ?: "", CategoryDescriptorDTO().setId(command.categoryId))
            }
            else -> {
                rocksDBOperations.getCategory(command.categoryId, true)
            }
        }
    }

    override fun getAggregate(event: EventDTO, rocksDBOperations: DBOperations): Category =
            rocksDBOperations.getCategory(event.categoryId, true)

/*
    override fun Payload.accept(aggregate: Category, event: EventDTO): Category {
        return when (this) {
            is StageStatusUpdatedPayload ->
                aggregate.stageStatusUpdated(this)
            is FightEditorChangesAppliedPayload ->
                aggregate.applyFightEditorChanges(this)
            is DashboardFightOrderChangedPayload ->
                aggregate.dashboardFightOrderChanged(this)
            is FightCompetitorsAssignedPayload -> aggregate.fightCompetitorsAssigned(this)
            is SetFightResultPayload -> aggregate.fightResultSet(this)
            is StageResultSetPayload -> aggregate.stageResultSet(this)
            is CompetitorsPropagatedToStagePayload -> aggregate.competitorsPropagatedToStage(this)
            is BracketsGeneratedPayload -> aggregate.bracketsGenerated(this)
            is FightsAddedToStagePayload -> aggregate.fightsAddedToStage(this)
            else -> throw EventApplyingException("Payload ${this.javaClass} not supported.", event)
        }
    }
*/

    override fun saveAggregate(aggregate: Category, rocksDBOperations: DBOperations): Category {
        rocksDBOperations.putCategory(aggregate)
        return aggregate
    }

    override val eventsToProcessors: Map<EventType, IEventHandler<Category>> = eventHandlers.filter { AggregateTypeDecider.getEventAggregateType(it.eventType) == AggregateType.CATEGORY }
            .groupBy { it.eventType }.mapValues { e -> e.value.first() }

    override fun isAggregateDeleted(event: EventDTO): Boolean {
        return event.type == EventType.CATEGORY_DELETED
    }
}