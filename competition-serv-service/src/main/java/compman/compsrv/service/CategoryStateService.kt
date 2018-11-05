package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.model.brackets.BracketDescriptor
import compman.compsrv.model.brackets.BracketType
import compman.compsrv.model.competition.*
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.schedule.MatScheduleContainer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class CategoryStateService constructor(private val fightsGenerateService: FightsGenerateService) : ICommandProcessingService<CategoryState, Command, EventHolder> {

    companion object {
        private val mapper: ObjectMapper = ObjectMapperFactory.createObjectMapper()
    }

    override fun apply(event: EventHolder, state: CategoryState?): Pair<CategoryState?, List<EventHolder>> {
        return if (state != null) {
            when (event.type) {
                EventType.COMPETITOR_ADDED -> applyCompetitorAddedEvent(state, event)
                EventType.COMPETITOR_REMOVED -> applyCompetitorRemovedEvent(state, event)
                EventType.COMPETITOR_UPDATED -> applyCompetitorUpdatedEvent(state, event)
                EventType.COMPETITORS_MOVED -> applyCompetitorsMovedEvent(state, event)
                EventType.BRACKETS_GENERATED -> applyBracketsGeneratedEvent(state, event)
                EventType.FIGHTS_START_TIME_UPDATED -> applyFighStartTimeUpdatedEvent(state, event)
                EventType.CATEGORY_STATE_DELETED -> applyCategoryStateDeletedEvent(state, event)
                EventType.CATEGORY_BRACKETS_DROPPED -> applyCategoryBracketsDroppedEvent(state, event)
                EventType.DUMMY -> state to listOf(event)
                else -> {
                    log.warn("Unknown event type: ${event.type}")
                    state to emptyList()
                }
            }
        } else {
            when (event.type) {
                EventType.CATEGORY_STATE_INITIALIZED -> applyInitCategoryStateEvent(event)
                else -> {
                    log.warn("Category state is null: ${event.type}")
                    state to emptyList()
                }
            }
        }
    }

    override fun process(command: Command, state: CategoryState?): List<EventHolder> {
        return when (command.type) {
            CommandType.INIT_CATEGORY_STATE_COMMAND -> if (state == null) processInitCategoryStateCommand(command) else {
                log.warn("Cannot add category ${command.categoryId} because it already exists ($state)")
                listOf(EventHolder(command.correlationId!!, command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, mapOf<String, Any?>(
                        "error" to "cannot add category ${command.categoryId} because it already exists ($state)",
                        "failedOn" to command
                )))
            }
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> doChangeCompetitorCategory(state, command)
            CommandType.CHANGE_COMPETITOR_FIGHT_COMMAND -> doMoveCompetitor(state, command)
            CommandType.GENERATE_BRACKETS_COMMAND -> doGenerateBrackets(state, command)
            CommandType.UPDATE_CATEGORY_FIGHTS_COMMAND -> doUpdateCategoryFights(state, command)
            CommandType.DELETE_CATEGORY_STATE_COMMAND -> doDeleteCategoryState(state, command)
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND -> doCreateFakeCompetitors(state, command)
            CommandType.DROP_CATEGORY_BRACKETS_COMMAND -> doDropCategoryBrackets(state, command)
            CommandType.DUMMY_COMMAND -> {
                listOf(createEvent(command, EventType.DUMMY, emptyMap()))
            }
            else -> {
                log.warn("Unknown command type: ${command.type}")
                listOf(EventHolder(command.correlationId!!, command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, mapOf<String, Any?>(
                        "exception" to "Unknown command type: ${command.type}",
                        "failedOn" to command)))
            }
        }
    }

    private fun applyCategoryBracketsDroppedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        return categoryState.withBrackets(null) to listOf(event)
    }

    private fun doDropCategoryBrackets(categoryState: CategoryState?, command: Command): List<EventHolder> = listOf(createEvent(command, EventType.CATEGORY_BRACKETS_DROPPED, command.payload
            ?: emptyMap()))

    private fun doUpdateCompetitor(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val competitor = getPayloadAs(command.payload?.get("fighter"), Competitor::class.java)
        return if (categoryState != null && competitor != null) {
            listOf(createEvent(command, EventType.COMPETITOR_UPDATED, mapOf("fighter" to competitor)))
        } else {
            listOf(createErrorEvent(command, "Competitor is null ${competitor == null} or category state is null ${categoryState == null}"))
        }
    }

    private fun applyCompetitorUpdatedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val competitor = getPayloadAs(event.payload?.get("fighter"), Competitor::class.java)
        return if (competitor != null && categoryState.competitors.any { it.id == competitor.id }) {
            categoryState.removeCompetitor(competitor.id).addCompetitor(competitor) to listOf(event)
        } else {
            categoryState to emptyList()
        }
    }

    private fun doChangeCompetitorCategory(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val competitor = getPayloadAs(command.payload?.get("fighter"), Competitor::class.java)
        val newCategory = getPayloadAs(command.payload?.get("newCategory"), CategoryDTO::class.java)
        return if (newCategory != null && categoryState != null && competitor != null) {
            val newCompetitor = competitor.copy(category = newCategory.toCategory())
            if (newCategory.categoryId == categoryState.category.categoryId) {
//                val newState = categoryState.addCompetitor(newCompetitor)
                listOf(createEvent(command, EventType.COMPETITOR_ADDED, mapper.convertValue(newCompetitor, Map::class.java) as Map<String, Any?>))
            } else {
                //this is old category, we need to delete fighter from here.
//                val newState = categoryState.removeCompetitor(competitor.email)
                listOf(createEvent(command, EventType.COMPETITOR_REMOVED, mapOf("competitorId" to competitor.id)))
//                newState to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, mapOf("competitorId" to competitor.email)))
            }
        } else {
            listOf(createErrorEvent(command, "New category is null ${newCategory == null} or category state is null ${categoryState == null}"))
        }
    }

    private fun applyCompetitorAddedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val competitor = getPayloadAs(event.payload, Competitor::class.java)
        return if (competitor != null) {
            val newCompetitor = competitor.copy(category = categoryState.category)
            val newState = categoryState.addCompetitor(newCompetitor)
            newState to listOf(event)
        } else {
            categoryState to listOf()
        }
    }


    private fun applyCompetitorRemovedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val competitorId = event.payload?.get("competitorId")?.toString()
        return if (competitorId != null) {
            val newState = categoryState.removeCompetitor(competitorId)
            newState to listOf(event)
        } else {
            categoryState to listOf()
        }
    }

    private fun applyCompetitorsMovedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val updatedSourceFight = getPayloadAs(event.payload?.get("updatedSourceFight"), FightDescription::class.java)
        val updatedTargetFight = getPayloadAs(event.payload?.get("updatedTargetFight"), FightDescription::class.java)
        return if (updatedSourceFight != null && updatedTargetFight != null) {
            val newState = categoryState.withBrackets(categoryState.brackets!!.setFights((categoryState.brackets!!.fights.filter { f -> f.fightId != updatedSourceFight.fightId && f.fightId != updatedTargetFight.fightId }
                    + updatedSourceFight
                    + updatedTargetFight).toTypedArray()))
            newState to listOf(event)
        } else {
            categoryState to emptyList()
        }
    }

    private fun doMoveCompetitor(categoryState: CategoryState?, command: Command): List<EventHolder> {
        if (categoryState != null) {
            val competitorId = command.payload?.get("competitorId")?.toString()
            val fromFightId = command.payload?.get("sourceFightId")?.toString()
            val toFightId = command.payload?.get("targetFightId")?.toString()
            val index = command.payload?.get("index")?.toString()?.toInt()
            if (competitorId.isNullOrBlank() || fromFightId.isNullOrBlank() || toFightId.isNullOrBlank() || categoryState.brackets?.fights == null || categoryState.brackets?.fights?.isEmpty() == true) {
                return listOf(createErrorEvent(command, "competitor ID or source fight ID or target fight ID is null."))
            }
            val sourceFight = categoryState.brackets?.fights?.find { it.fightId == fromFightId }
            val targetFight = categoryState.brackets?.fights?.find { it.fightId == toFightId }
            if (sourceFight != null && targetFight != null) {
                val compScorePair = sourceFight.competitors.find { it.competitor.id == competitorId }
                if (compScorePair != null) {
                    when {
                        targetFight.competitors.size < 2 -> {
                            var tmpInd = 1
                            if (index != null && index >= 0 && index < 2) {
                                tmpInd = index
                            }
                            val updatedSourceFight = sourceFight.copy(competitors = sourceFight.competitors.filter { it.competitor.id != compScorePair.competitor.id }.toTypedArray())
                            val updatedTargetFight = targetFight.setCompetitorWithIndex(compScorePair.competitor, tmpInd)
                            return listOf(createEvent(command, EventType.COMPETITORS_MOVED, mapOf("updatedSourceFight" to updatedSourceFight, "updatedTargetFight" to updatedTargetFight)))
                        }
                        targetFight.competitors.size == 2 -> {
                            //need to swap
                            var tmpInd = 1
                            if (index != null && index >= 0 && index < 2) {
                                tmpInd = index
                            }
                            val competitorToSwap = targetFight.competitors.drop(tmpInd).first()
                            val updatedSourceFight = sourceFight.copy(competitors = ((sourceFight.competitors.filter { it.competitor.id != compScorePair.competitor.id }) + competitorToSwap).toTypedArray())
                            val updatedTargetFight = targetFight.setCompetitorWithIndex(compScorePair.competitor, tmpInd)
                            return listOf(createEvent(command, EventType.COMPETITORS_MOVED, mapOf("updatedSourceFight" to updatedSourceFight, "updatedTargetFight" to updatedTargetFight)))
                        }
                        else -> //strange... do nothing and throw error
                            return listOf(createErrorEvent(command, "Number of competitors in the target fight is greater than 2: $targetFight"))
                    }
                } else {
                    return listOf(createErrorEvent(command, "Cannot find competitor with id $competitorId in fight $sourceFight"))
                }
            } else {
                return listOf(createErrorEvent(command, "Cannot find source or target fight."))
            }

        } else {
            return listOf(createErrorEvent(command, "CategoryState is null."))
        }
    }

    private fun createErrorEvent(command: Command, errorStr: String) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, mapOf("error" to errorStr))
    private fun createEvent(command: Command, eventType: EventType, payload: Map<String, Any?>) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId, command.matId, eventType, payload)

    private fun doUpdateCategoryFights(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val matScheduleContainers = getPayloadAs(command.payload?.get("fightsByMats"), Array<MatScheduleContainer>::class.java)
        val matIdToFight = matScheduleContainers?.map { it.matId to it.fights }?.toMap()
        fun getMat(fightId: String) = matIdToFight?.entries?.find { entry -> entry.value.find { it.fightId == fightId } != null }?.key
                ?: ""

        val fights = matScheduleContainers?.flatMap { it.fights }
        val updatedFights = fights
                ?.mapNotNull {
                    categoryState?.brackets?.fights?.find { fd -> fd.fightId == it.fightId }
                            ?.setStartTime(it.startTime)
                            ?.setNumberOnMat(it.orderOnTheMat)
                            ?.setMat(getMat(it.fightId))
                }
                ?: emptyList()
        val notUpdatedFights = categoryState?.brackets?.fights?.filterNot { f -> updatedFights.any { it.fightId == f.fightId } == true }
                ?: emptyList()
        val newFights = notUpdatedFights + updatedFights
//        return categoryState?.withBrackets(categoryState.brackets?.setFights(newFights.toTypedArray())) to listOf(createEvent(command, EventType.FIGHTS_START_TIME_UPDATED, mapOf("newFights" to newFights)))
        return listOf(createEvent(command, EventType.FIGHTS_START_TIME_UPDATED, mapOf("newFights" to newFights)))
    }

    private fun applyFighStartTimeUpdatedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val newFights = getPayloadAs(event.payload?.get("newFights"), Array<FightDescription>::class.java)
        return if (newFights != null) {
            categoryState.withBrackets(categoryState.brackets?.setFights(newFights)) to listOf(event)
        } else {
            categoryState to emptyList()
        }
    }

    private fun applyBracketsGeneratedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val fights = getPayloadAs(event.payload?.get("fights"), Array<FightDescription>::class.java)
        val bracketType = getPayloadAs(event.payload?.get("bracketType"), BracketType::class.java)
        return if (fights != null) {
            categoryState.withBrackets(BracketDescriptor(bracketType ?: BracketType.SINGLE_ELIMINATION, fights)) to listOf(event)
        } else {
            categoryState to emptyList()
        }
    }


    private fun doGenerateBrackets(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val fights = fightsGenerateService.generatePlayOff(categoryState?.competitors?.toList(), command.competitionId)
        val bracketType = BracketType.valueOf(command.payload?.get("bracketType")?.toString()
                ?: BracketType.SINGLE_ELIMINATION.name)
//        return categoryState?.withBrackets(BracketDescriptor(bracketType, fights.toTypedArray())) to listOf(createEvent(command, EventType.BRACKETS_GENERATED, mapOf("fights" to fights)))
        return listOf(createEvent(command, EventType.BRACKETS_GENERATED, mapOf("fights" to fights)))
    }

    private fun doAddCompetitor(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val competitor = getPayloadAs(command.payload, Competitor::class.java)
        return if (competitor != null && categoryState != null) {
//            categoryState.addCompetitor(competitor) to listOf(createEvent(command, EventType.COMPETITOR_ADDED, command.payload
//                    ?: emptyMap()))
            listOf(createEvent(command, EventType.COMPETITOR_ADDED, command.payload
                    ?: emptyMap()))
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, mapOf<String, Any?>(
                    "exception" to "Failed to get competitor from payload. Or category state is null. (${categoryState == null})",
                    "failedOn" to command)))
        }
    }

    private fun doRemoveCompetitor(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val competitorId = command.payload?.get("competitorId")?.toString()
        return if (!competitorId.isNullOrBlank() && categoryState != null) {
            listOf(createEvent(command, EventType.COMPETITOR_REMOVED, command.payload
                    ?: emptyMap()))
//            categoryState.removeCompetitor(competitorId!!) to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, command.payload
//                    ?: emptyMap()))
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, mapOf<String, Any?>(
                    "exception" to "Failed to get competitor id from payload. Or category state is null. (${categoryState == null})",
                    "failedOn" to command)))
        }
    }

    private fun processInitCategoryStateCommand(command: Command): List<EventHolder> {
        val c = getPayloadAs(command.payload?.get("category"), Category::class.java)
        return if (c != null && command.categoryId != null) {
            val category = c.setCategoryId(command.categoryId!!)
            val state = CategoryState(command.correlationId!!, category, null, emptySet())
            listOf(createEvent(command, EventType.CATEGORY_STATE_INITIALIZED, mapOf("categoryState" to state)))
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, mapOf<String, Any?>(
                    "exception" to "Failed to get category from command payload",
                    "failedOn" to command)))
        }
    }

    private fun applyInitCategoryStateEvent(event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val c = getPayloadAs(event.payload?.get("categoryState"), CategoryState::class.java)
        return if (c != null && event.categoryId != null) {
            c to listOf(event)
        } else {
            throw EventApplyingException("event did not contain category state.")
        }
    }

    private fun doDeleteCategoryState(categoryState: CategoryState?, command: Command) = listOf(createEvent(command, EventType.CATEGORY_STATE_DELETED, mapOf("categoryState" to categoryState)))
    private fun applyCategoryStateDeletedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        return categoryState.copy(status = CategoryStateStatus.DELETED) to listOf(event)
    }


    private fun doCreateFakeCompetitors(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val numberOfCompetitors = command.payload?.get("numberOfCompetitors")?.toString()?.toInt() ?: 50
        val numberOfAcademies = command.payload?.get("numberOfAcademies")?.toString()?.toInt() ?: 30
        val fakeCompetitors = FightsGenerateService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, categoryState!!.category, categoryState.category.competitionId)
        val finalCategoryState = fakeCompetitors.fold(categoryState) { acc, competitor -> acc.addCompetitor(competitor) }
        val events = fakeCompetitors.map {
            createEvent(command, EventType.COMPETITOR_ADDED, mapper.convertValue(it, Map::class.java) as? Map<String, Any?>?
                    ?: emptyMap())
        }
//        return finalCategoryState to events
        return events
    }

    private val log = LoggerFactory.getLogger(CategoryStateService::class.java)


    private fun <T> getPayloadAs(payload: Any?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.convertValue(payload, clazz)
        }
        return null
    }
}