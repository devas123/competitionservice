package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.model.brackets.BracketDescriptor
import compman.compsrv.model.brackets.BracketType
import compman.compsrv.model.competition.Category
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.schedule.MatScheduleContainer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
open class CategoryStateService constructor(private val fightsGenerateService: FightsGenerateService) {

    companion object {
        private val mapper: ObjectMapper = ObjectMapperFactory.createObjectMapper()
    }

    fun executeCommand(command: Command, categoryState: CategoryState?): Pair<CategoryState?, List<EventHolder>> {
        return when (command.type) {
            CommandType.INIT_CATEGORY_STATE_COMMAND -> if (categoryState == null) doInitCategoryState(command) else {
                log.warn("Cannot add category ${command.categoryId} because it already exists ($categoryState)")
                categoryState to listOf(EventHolder(command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, mapOf<String, Any?>(
                        "error" to "cannot add category ${command.categoryId} because it already exists ($categoryState)",
                        "failedOn" to command
                )))
            }
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> doChangeCompetitorCategory(categoryState, command)
            CommandType.UPDATE_COMPETITOR_COMMAND -> doUpdateCompetitor(categoryState, command)
            CommandType.MOVE_COMPETITOR_COMMAND -> doMoveCompetitor(categoryState, command)
            CommandType.GENERATE_BRACKETS_COMMAND -> doGenerateBrackets(categoryState, command)
            CommandType.UPDATE_CATEGORY_FIGHTS_COMMAND -> doUpdateCategoryFights(categoryState, command)
            CommandType.DELETE_CATEGORY_STATE_COMMAND -> doDeleteCategoryState(categoryState, command)
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND -> doCreateFakeCompetitors(categoryState, command)
            CommandType.DROP_CATEGORY_BRACKETS_COMMAND -> doDropCategoryBrackets(categoryState, command)
            CommandType.ADD_COMPETITOR_COMMAND -> doAddCompetitor(categoryState, command)
            CommandType.REMOVE_COMPETITOR_COMMAND -> doRemoveCompetitor(categoryState, command)
            CommandType.DUMMY_COMMAND -> {
                categoryState to listOf(createEvent(command, EventType.DUMMY, emptyMap()))
            }
            else -> {
                log.warn("Unknown command type: ${command.type}")
                categoryState to listOf(EventHolder(command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, mapOf<String, Any?>(
                        "exception" to "Unknown command type: ${command.type}",
                        "failedOn" to command)))
            }
        }
    }

    private fun doDropCategoryBrackets(categoryState: CategoryState?, command: Command): Pair<CategoryState?, List<EventHolder>> = categoryState?.copy(brackets = null) to listOf(createEvent(command, EventType.CATEGORY_BRACKETS_DROPPED, command.payload
            ?: emptyMap()))

    private fun doUpdateCompetitor(categoryState: CategoryState?, command: Command): Pair<CategoryState?, List<EventHolder>> {
        val competitor = getPayloadAs(command.payload?.get("fighter"), Competitor::class.java)
        return if (categoryState != null && competitor != null) {
            categoryState.removeCompetitor(competitor.email).addCompetitor(competitor) to listOf(createEvent(command, EventType.COMPETITOR_UPDATED, mapOf("fighter" to competitor)))
        } else {
            categoryState to listOf(createErrorEvent(command, "Competitor is null ${competitor == null} or category state is null ${categoryState == null}"))
        }
    }

    private fun doChangeCompetitorCategory(categoryState: CategoryState?, command: Command): Pair<CategoryState?, List<EventHolder>> {
        val competitor = getPayloadAs(command.payload?.get("fighter"), Competitor::class.java)
        val newCategory = getPayloadAs(command.payload?.get("newCategory"), CategoryDTO::class.java)
        return if (newCategory != null && categoryState != null && competitor != null) {
            val newCompetitor = competitor.copy(category = newCategory.toCategory())
            if (newCategory.categoryId == categoryState.category.categoryId) {
                val newState = categoryState.addCompetitor(newCompetitor)
                newState to listOf(createEvent(command, EventType.COMPETITOR_ADDED, mapper.convertValue(newCompetitor, Map::class.java) as Map<String, Any?>))
            } else {
                //this is old category, we need to delete fighter from here.
                val newState = categoryState.removeCompetitor(competitor.email)
                newState to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, mapOf("competitorId" to competitor.email)))
            }
        } else {
            categoryState to listOf(createErrorEvent(command, "New category is null ${newCategory == null} or category state is null ${categoryState == null}"))
        }
    }

    private fun doMoveCompetitor(categoryState: CategoryState?, command: Command): Pair<CategoryState?, List<EventHolder>> {
        if (categoryState != null) {
            val competitorId = command.payload?.get("competitorId")?.toString()
            val fromFightId = command.payload?.get("sourceFightId")?.toString()
            val toFightId = command.payload?.get("targetFightId")?.toString()
            val index = command.payload?.get("index")?.toString()?.toInt()
            if (competitorId.isNullOrBlank() || fromFightId.isNullOrBlank() || toFightId.isNullOrBlank() || categoryState.brackets?.fights == null || categoryState.brackets?.fights?.isEmpty() == true) {
                return categoryState to listOf(createErrorEvent(command, "competitor ID or source fight ID or target fight ID is null."))
            }
            val sourceFight = categoryState.brackets?.fights?.find { it.fightId == fromFightId }
            val targetFight = categoryState.brackets?.fights?.find { it.fightId == toFightId }
            if (sourceFight != null && targetFight != null) {
                val compScorePair = sourceFight.competitors.find { it.competitor.email == competitorId }
                if (compScorePair != null) {
                    when {
                        targetFight.competitors.size < 2 -> {
                            var tmpInd = 1
                            if (index != null && index >= 0 && index < 2) {
                                tmpInd = index
                            }
                            val updatedSourceFight = sourceFight.copy(competitors = sourceFight.competitors.filter { it.competitor.email != compScorePair.competitor.email }.toTypedArray())
                            val updatedTargetFight = targetFight.setCompetitorWithIndex(compScorePair.competitor, tmpInd)
                            val newState = categoryState.withBrackets(categoryState.brackets!!.setFights((categoryState.brackets!!.fights.filter { f -> f.fightId != updatedSourceFight.fightId && f.fightId != updatedTargetFight.fightId }
                                    + updatedSourceFight
                                    + updatedTargetFight).toTypedArray()))
                            return newState to listOf(createEvent(command, EventType.COMPETITORS_MOVED, mapOf("updatedSourceFight" to updatedSourceFight, "updatedTargetFight" to updatedTargetFight)))
                        }
                        targetFight.competitors.size == 2 -> {
                            //need to swap
                            var tmpInd = 1
                            if (index != null && index >= 0 && index < 2) {
                                tmpInd = index
                            }
                            val competitorToSwap = targetFight.competitors.drop(tmpInd).first()
                            val updatedSourceFight = sourceFight.copy(competitors = ((sourceFight.competitors.filter { it.competitor.email != compScorePair.competitor.email }) + competitorToSwap).toTypedArray())
                            val updatedTargetFight = targetFight.setCompetitorWithIndex(compScorePair.competitor, tmpInd)
                            val newState = categoryState.withBrackets(categoryState.brackets!!.setFights((categoryState.brackets!!.fights.filter { f -> f.fightId != updatedSourceFight.fightId && f.fightId != updatedTargetFight.fightId }
                                    + updatedSourceFight
                                    + updatedTargetFight).toTypedArray()))
                            return newState to listOf(createEvent(command, EventType.COMPETITORS_MOVED, mapOf("updatedSourceFight" to updatedSourceFight, "updatedTargetFight" to updatedTargetFight)))
                        }
                        else -> //strange... do nothing and throw error
                            return categoryState to listOf(createErrorEvent(command, "Number of competitors in the target fight is greater than 2: $targetFight"))
                    }
                } else {
                    return categoryState to listOf(createErrorEvent(command, "Cannot find competitor with id $competitorId in fight $sourceFight"))
                }
            } else {
                return categoryState to listOf(createErrorEvent(command, "Cannot find source or target fight."))
            }

        } else {
            return categoryState to listOf(createErrorEvent(command, "CategoryState is null."))
        }
    }

    private fun createErrorEvent(command: Command, errorStr: String) = EventHolder(command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, mapOf("error" to errorStr))
    private fun createEvent(command: Command, eventType: EventType, payload: Map<String, Any?>) = EventHolder(command.competitionId, command.categoryId, command.matId, eventType, payload)

    private fun doUpdateCategoryFights(categoryState: CategoryState?, command: Command): Pair<CategoryState?, List<EventHolder>> {
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
        return categoryState?.withBrackets(categoryState.brackets?.setFights(newFights.toTypedArray())) to listOf(createEvent(command, EventType.FIGHTS_START_TIME_UPDATED, mapOf("newFights" to newFights)))
    }


    private fun doGenerateBrackets(categoryState: CategoryState?, command: Command): Pair<CategoryState?, List<EventHolder>> {
        val fights = fightsGenerateService.generatePlayOff(categoryState?.competitors?.toList(), command.competitionId)
        val bracketType = BracketType.valueOf(command.payload?.get("bracketType")?.toString()
                ?: BracketType.SINGLE_ELIMINATION.name)
        return categoryState?.withBrackets(BracketDescriptor(bracketType, fights.toTypedArray())) to listOf(createEvent(command, EventType.BRACKETS_GENERATED, mapOf("fights" to fights)))
    }

    private fun doAddCompetitor(categoryState: CategoryState?, command: Command): Pair<CategoryState?, List<EventHolder>> {
        val competitor = getPayloadAs(command.payload, Competitor::class.java)
        return if (competitor != null && categoryState != null) {
            categoryState.addCompetitor(competitor) to listOf(createEvent(command, EventType.COMPETITOR_ADDED, command.payload
                    ?: emptyMap()))
        } else {
            categoryState to listOf(createEvent(command, EventType.ERROR_EVENT, mapOf<String, Any?>(
                    "exception" to "Failed to get competitor from payload. Or category state is null. (${categoryState == null})",
                    "failedOn" to command)))
        }
    }

    private fun doRemoveCompetitor(categoryState: CategoryState?, command: Command): Pair<CategoryState?, List<EventHolder>> {
        val competitorId = command.payload?.get("competitorId")?.toString()
        return if (!competitorId.isNullOrBlank() && categoryState != null) {
            categoryState.removeCompetitor(competitorId!!) to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, command.payload
                    ?: emptyMap()))
        } else {
            categoryState to listOf(createEvent(command, EventType.ERROR_EVENT, mapOf<String, Any?>(
                    "exception" to "Failed to get competitor id from payload. Or category state is null. (${categoryState == null})",
                    "failedOn" to command)))
        }
    }

    private fun doInitCategoryState(command: Command): Pair<CategoryState?, List<EventHolder>> {
        val c = getPayloadAs(command.payload?.get("category"), Category::class.java)
        return if (c != null && command.categoryId != null) {
            val category = c.setCategoryId(command.categoryId!!)
            val state = CategoryState(-1L, -1, category, null, emptySet())
            state to listOf(createEvent(command, EventType.CATEGORY_STATE_ADDED, mapOf("categoryState" to state)))
        } else {
            null to listOf(createEvent(command, EventType.ERROR_EVENT, mapOf<String, Any?>(
                    "exception" to "Failed to get category from command payload",
                    "failedOn" to command)))
        }
    }

    private fun doDeleteCategoryState(categoryState: CategoryState?, command: Command) = null to listOf(createEvent(command, EventType.CATEGORY_STATE_DELETED, mapOf("categoryState" to categoryState)))
    private fun doCreateFakeCompetitors(categoryState: CategoryState?, command: Command): Pair<CategoryState?, List<EventHolder>> {
        val numberOfCompetitors = command.payload?.get("numberOfCompetitors")?.toString()?.toInt() ?: 50
        val numberOfAcademies = command.payload?.get("numberOfAcademies")?.toString()?.toInt() ?: 30
        val fakeCompetitors = FightsGenerateService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, categoryState!!.category, categoryState.category.competitionId)
        val finalCategoryState = fakeCompetitors.fold(categoryState) { acc, competitor -> acc.addCompetitor(competitor) }
        val events = fakeCompetitors.map {
            createEvent(command, EventType.COMPETITOR_ADDED, mapper.convertValue(it, Map::class.java) as? Map<String, Any?>?
                    ?: emptyMap())
        }
        return finalCategoryState to events
    }

    private val log = LoggerFactory.getLogger(CategoryStateService::class.java)


    private fun <T> getPayloadAs(payload: Any?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.convertValue(payload, clazz)
        }
        return null
    }
}