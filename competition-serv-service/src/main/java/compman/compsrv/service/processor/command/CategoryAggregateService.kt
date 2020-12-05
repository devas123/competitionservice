package compman.compsrv.service.processor.command

import arrow.core.fix
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.CategoryRestrictionDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.Rules
import org.springframework.stereotype.Component


@Component
class CategoryAggregateService constructor(
    private val fightsGenerateService: FightServiceFactory,
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
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

    private val doChangeCategoryRegistrationStatus: CommandExecutor<Category> = { category, _, com ->
        category to listOf(createEvent(com, EventType.CATEGORY_REGISTRATION_STATUS_CHANGED, com.payload))
    }

    private val doDropCategoryBrackets: CommandExecutor<Category> = { category, _, com ->
        category to listOf(createEvent(com, EventType.CATEGORY_BRACKETS_DROPPED, com.payload))
    }

    private val doApplyFightsEditorChanges: CommandExecutor<Category> = { category, _, com ->
        executeValidated(com, FightEditorApplyChangesPayload::class.java) { payload, command ->
            category to category.process(payload, command, this::createEvent)
        }.unwrap(com)
    }

    private val doGenerateBrackets: CommandExecutor<Category> = { category, rocksDB, com ->
        executeValidated(com, GenerateBracketsPayload::class.java) { payload, command ->
            val competitors = rocksDB.getCategoryCompetitors(command.competitionId, command.categoryId, false)
            if (!competitors.isNullOrEmpty()) {
                category to category.process(
                    payload,
                    command,
                    fightsGenerateService,
                    competitors.map { it.competitorDTO },
                    this::createEvent
                )
            } else {
                throw IllegalArgumentException("No competitors or category not found.")
            }
        }.unwrap(com)
    }


    private val updateStageStatus: CommandExecutor<Category> = { category, _, command ->
        executeValidated(command, UpdateStageStatusPayload::class.java) { payload, c ->
            category to category.process(payload, c, this::createEvent)
        }.unwrap(command)
    }


    private fun CategoryRestrictionDTO.withId(): CategoryRestrictionDTO = this.setId(IDGenerator.restrictionId(this))

    private val processAddCategoryCommandDTO: CommandExecutor<Category> = { _, rocksDb, command ->
        val c = mapper.convertValue(command.payload, AddCategoryPayload::class.java)?.category
        if (c != null && !c.restrictions.isNullOrEmpty()) {
            val restrictionsValid = Rules.accumulateErrors {
                c.restrictions.map { it.validate() }
            }.map { it.fix() }
            val categoryId = command.categoryId
                ?: IDGenerator.hashString("${command.competitionId}/${IDGenerator.categoryId(c)}")
            if (!rocksDb.categoryExists(categoryId)) {
                if (restrictionsValid.all { restriction -> restriction.isValid }) {
                    val registrationOpen = c.registrationOpen ?: true
                    val state = c
                        .setRestrictions(c.restrictions.map { it.withId() }.toTypedArray())
                        .setId(categoryId).setRegistrationOpen(registrationOpen)
                    Category(categoryId, state) to listOf(
                        createEvent(
                            command,
                            EventType.CATEGORY_ADDED,
                            CategoryAddedPayload(state)
                        ).setCategoryId(categoryId)
                    )
                } else {
                    throw IllegalArgumentException(restrictionsValid.fold(StringBuilder()) { acc, r ->
                        acc.append(
                            r.fold(
                                { it.toList().joinToString(",") },
                                { "" })
                        )
                    }.toString())
                }
            } else {
                throw IllegalArgumentException("Category with ID $categoryId already exists.")
            }
        } else {
            throw IllegalArgumentException("Failed to get category from command payload")
        }
    }

    private val doDeleteCategoryState: CommandExecutor<Category> =
        { category, rocksDBOperations: DBOperations, command: CommandDTO ->
            if (rocksDBOperations.getCategoryCompetitors(command.competitionId, command.categoryId, false)
                    .isNullOrEmpty() && category.fights.isNullOrEmpty()
            ) {
                category to listOf(createEvent(command, EventType.CATEGORY_DELETED, command.payload))
            } else {
                throw IllegalArgumentException("There are already competitors registered to this category. Please move them to another category first.")
            }
        }


    private val propagateCompetitors: CommandExecutor<Category> = { category, rocksDBOperations, command ->
        executeValidated(command, PropagateCompetitorsPayload::class.java) { p, com ->
            val competitors = rocksDBOperations.getCategoryCompetitors(command.competitionId, command.categoryId, false)
            category to category.process(
                p,
                com,
                competitors.map { it.competitorDTO },
                fightsGenerateService,
                this::createEvent
            )
        }.unwrap(command)
    }

    private val setFightResult: CommandExecutor<Category> = { category, _, com ->
        executeValidated(com, SetFightResultPayload::class.java) { payload, command ->
            category to category.process(payload, command, fightsGenerateService, this::createEvent)
        }.unwrap(com)
    }


    private val changeFightOrder: CommandExecutor<Category> = { category, _, command ->
        executeValidated(command, DashboardFightOrderChangePayload::class.java) { payload, _ ->
            category to category.process(payload, command, this::createEvent)
        }.unwrap(command)
    }


    override val commandsToHandlers: Map<CommandType, CommandExecutor<Category>> = mapOf(
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
    )

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

    override val eventsToPayloads: Map<EventType, Class<out Payload>> = mapOf(
        EventType.STAGE_STATUS_UPDATED to StageStatusUpdatedPayload::class.java,
        EventType.FIGHTS_EDITOR_CHANGE_APPLIED to FightEditorChangesAppliedPayload::class.java,
        EventType.DASHBOARD_FIGHT_ORDER_CHANGED to DashboardFightOrderChangedPayload::class.java,
        EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED to FightCompetitorsAssignedPayload::class.java,
        EventType.DASHBOARD_FIGHT_RESULT_SET to SetFightResultPayload::class.java,
        EventType.DASHBOARD_STAGE_RESULT_SET to StageResultSetPayload::class.java,
        EventType.COMPETITORS_PROPAGATED_TO_STAGE to CompetitorsPropagatedToStagePayload::class.java,
        EventType.BRACKETS_GENERATED to BracketsGeneratedPayload::class.java,
        EventType.FIGHTS_ADDED_TO_STAGE to FightsAddedToStagePayload::class.java
    )

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
}