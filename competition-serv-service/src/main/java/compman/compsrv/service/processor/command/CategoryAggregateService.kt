package compman.compsrv.service.processor.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.GroupChangeType
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component


@Component
class CategoryAggregateService constructor(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>,
    commandExecutors: List<ICommandExecutor<Category>>
) : AbstractAggregateService<Category>(mapper, validators) {

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


    override val commandsToHandlers: Map<CommandType, CommandExecutor<Category>> = commandsToExecutors.mapValues { e -> { cat: Category, ops: DBOperations, c: CommandDTO -> e.value.execute(cat, ops, c) } } /*mapOf(
        CommandType.UPDATE_STAGE_STATUS_COMMAND to updateStageStatus,
        CommandType.ADD_CATEGORY_COMMAND to processAddCategoryCommandDTO,
        CommandType.FIGHTS_EDITOR_APPLY_CHANGE to doApplyFightsEditorChanges,
        CommandType.GENERATE_BRACKETS_COMMAND to doGenerateBrackets,
        CommandType.DELETE_CATEGORY_COMMAND to doDeleteCategoryState,
        CommandType.CHANGE_CATEGORY_REGISTRATION_STATUS_COMMAND to doChangeCategoryRegistrationStatus,
        CommandType.DROP_CATEGORY_BRACKETS_COMMAND to doDropCategoryBrackets,
        CommandType.PROPAGATE_COMPETITORS_COMMAND to propagateCompetitors,
        CommandType.DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND to changeFightOrder,
        CommandType.DASHBOARD_SET_FIGHT_RESULT_COMMAND to setFightResult
    )*/

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

    override fun saveAggregate(aggregate: Category, rocksDBOperations: DBOperations): Category {
        rocksDBOperations.putCategory(aggregate)
        return aggregate
    }

    override fun applySimpleEvent(aggregate: Category, event: EventDTO): Category {

    }
}