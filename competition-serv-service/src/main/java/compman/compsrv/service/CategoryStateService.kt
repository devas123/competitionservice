package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.brackets.BracketDescriptor
import compman.compsrv.model.brackets.BracketType
import compman.compsrv.model.competition.CategoryDescriptor
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.CategoryStateStatus
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.es.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.model.schedule.MatScheduleContainer
import compman.compsrv.repository.CompetitionPropertiesCrudRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.ZonedDateTime


@Component
class CategoryStateService constructor(private val fightsGenerateService: FightsGenerateService, private val mapper: ObjectMapper, private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository) : ICommandProcessingService<CategoryState, Command, EventHolder> {

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
                listOf(EventHolder(command.correlationId, command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, mapper.writeValueAsBytes(mapOf<String, Any?>(
                        "error" to "cannot add category ${command.categoryId} because it already exists ($state)",
                        "failedOn" to command
                ))))
            }
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> doChangeCompetitorCategory(state, command)
            CommandType.CHANGE_COMPETITOR_FIGHT_COMMAND -> doMoveCompetitor(state, command)
            CommandType.GENERATE_BRACKETS_COMMAND -> doGenerateBrackets(state, command)
            CommandType.UPDATE_CATEGORY_FIGHTS_COMMAND -> doUpdateCategoryFights(state, command)
            CommandType.DELETE_CATEGORY_STATE_COMMAND -> doDeleteCategoryState(state, command)
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND -> doCreateFakeCompetitors(state, command)
            CommandType.DROP_CATEGORY_BRACKETS_COMMAND -> doDropCategoryBrackets(command)
            CommandType.DUMMY_COMMAND -> {
                listOf(createEvent(command, EventType.DUMMY, emptyMap<Any, Any>()))
            }
            else -> {
                log.warn("Unknown command type: ${command.type}")
                listOf(EventHolder(command.correlationId, command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, mapper.writeValueAsBytes(mapOf<String, Any?>(
                        "exception" to "Unknown command type: ${command.type}",
                        "failedOn" to command))))
            }
        }
    }

    private fun applyCategoryBracketsDroppedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        return categoryState.withBrackets(null) to listOf(event)
    }

    private fun doDropCategoryBrackets(command: Command): List<EventHolder> = listOf(createEvent(command, EventType.CATEGORY_BRACKETS_DROPPED, command.payload
            ?: emptyMap<Any, Any>()))

    private fun doUpdateCompetitor(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val competitor = getPayloadAs(command.payload?.get("fighter"), Competitor::class.java)
        return if (categoryState != null && competitor != null) {
            listOf(createEvent(command, EventType.COMPETITOR_UPDATED, mapOf("fighter" to competitor)))
        } else {
            listOf(createErrorEvent(command, "Competitor is null ${competitor == null} or category state is null ${categoryState == null}"))
        }
    }

    private fun applyCompetitorUpdatedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val payload = getPayloadAs(event.payload, CompetitorUpdatedPayload::class.java)
        val competitor = payload?.fighter
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
            val newCompetitor = competitor.copy(categoryId = newCategory.categoryId)
            if (newCategory.categoryId == categoryState.category.id) {
//                val newState = categoryState.addCompetitor(newCompetitor)
                listOf(createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(newCompetitor)))
            } else {
                //this is old category, we need to delete fighter from here.
//                val newState = categoryState.removeCompetitor(competitor.email)
                listOf(createEvent(command, EventType.COMPETITOR_REMOVED, CompetitorRemovedPayload(competitor.id)))
//                newState to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, mapOf("competitorId" to competitor.email)))
            }
        } else {
            listOf(createErrorEvent(command, "New category is null ${newCategory == null} or category state is null ${categoryState == null}"))
        }
    }

    private fun applyCompetitorAddedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val competitor = getPayloadAs(event.payload, Competitor::class.java)
        return if (competitor != null) {
            val newCompetitor = competitor.copy(categoryId = categoryState.category.id)
            val newState = categoryState.addCompetitor(newCompetitor)
            newState to listOf(event)
        } else {
            categoryState to listOf()
        }
    }


    private fun applyCompetitorRemovedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val competitorId = getPayloadAs(event.payload, CompetitorRemovedPayload::class.java)?.fighterId
        return if (competitorId != null) {
            val newState = categoryState.removeCompetitor(competitorId)
            newState to listOf(event)
        } else {
            categoryState to listOf()
        }
    }

    private fun applyCompetitorsMovedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val payload = getPayloadAs(event.payload, CompetitorMovedPayload::class.java)
        val updatedSourceFight = payload?.updatedSourceFight
        val updatedTargetFight = payload?.updatedTargetFight
        return if (updatedSourceFight != null && updatedTargetFight != null) {
            val newState = categoryState.withBrackets(categoryState.brackets!!.setFights((categoryState.brackets.fights.filter { f -> f.id != updatedSourceFight.id && f.id != updatedTargetFight.id }
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
            if (competitorId.isNullOrBlank() || fromFightId.isNullOrBlank() || toFightId.isNullOrBlank() || categoryState.brackets?.fights == null || categoryState.brackets.fights.isEmpty()) {
                return listOf(createErrorEvent(command, "competitor ID or source fight ID or target fight ID is null."))
            }
            val sourceFight = categoryState.brackets.fights.find { it.id == fromFightId }
            val targetFight = categoryState.brackets.fights.find { it.id == toFightId }
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

    private fun createErrorEvent(command: Command, errorStr: String) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, mapper.writeValueAsBytes(mapOf("error" to errorStr)))
    private fun createEvent(command: Command, eventType: EventType, payload: Any?) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId, command.matId, eventType, mapper.writeValueAsBytes(payload))

    private fun doUpdateCategoryFights(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val matScheduleContainers = getPayloadAs(command.payload?.get("fightsByMats"), Array<MatScheduleContainer>::class.java)
        val matIdToFight = matScheduleContainers?.map { it.matId to it.fights }?.toMap()
        fun getMat(fightId: String) = matIdToFight?.entries?.find { entry -> entry.value.find { it.fight.id == fightId } != null }?.key
                ?: ""

        val fights = matScheduleContainers?.flatMap { it.fights }
        val updatedFights = fights
                ?.mapNotNull {
                    categoryState?.brackets?.fights?.find { fd -> fd.id == it.fight.id }
                            ?.setStartTime(ZonedDateTime.ofInstant(it.startTime.toInstant(), ZoneId.of(categoryState.competition.timeZone)))
                            ?.setNumberOnMat(it.fightNumber)
                            ?.setMat(getMat(it.fight.id))
                }
                ?: emptyList()
        val notUpdatedFights = categoryState?.brackets?.fights?.filterNot { f -> updatedFights.any { it.id == f.id } }
                ?: emptyList()
        val newFights = notUpdatedFights + updatedFights
//        return categoryState?.withBrackets(categoryState.brackets?.setFights(newFights.toTypedArray())) to listOf(createEvent(command, EventType.FIGHTS_START_TIME_UPDATED, mapOf("newFights" to newFights)))
        return listOf(createEvent(command, EventType.FIGHTS_START_TIME_UPDATED, mapOf("newFights" to newFights)))
    }

    private fun applyFighStartTimeUpdatedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val payload = getPayloadAs(event.payload, FightStartTimeUpdatedPayload::class.java)
        val newFights = payload?.newFights
        return if (newFights != null) {
            categoryState.withBrackets(categoryState.brackets?.setFights(newFights)) to listOf(event)
        } else {
            categoryState to emptyList()
        }
    }

    private fun applyBracketsGeneratedEvent(categoryState: CategoryState, event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val payload = getPayloadAs(event.payload, BracketsGeneratedPayload::class.java)
        val fights = payload?.fights
        val bracketType = payload?.bracketType
        return if (fights != null) {
            categoryState.withBrackets(BracketDescriptor(categoryState.id, categoryState.competition.id, bracketType
                    ?: BracketType.SINGLE_ELIMINATION, fights)) to listOf(event)
        } else {
            categoryState to emptyList()
        }
    }


    private fun doGenerateBrackets(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val fights = fightsGenerateService.generatePlayOff(categoryState?.competitors?.toList(), command.competitionId)
//        val bracketType = BracketType.valueOf(command.payload?.get("bracketType")?.toString()
//                ?: BracketType.SINGLE_ELIMINATION.name)
        return listOf(createEvent(command, EventType.BRACKETS_GENERATED, mapOf("fights" to fights)))
    }

    private fun doAddCompetitor(categoryState: CategoryState?, command: Command): List<EventHolder> {
        val competitor = getPayloadAs(command.payload, Competitor::class.java)
        return if (competitor != null && categoryState != null) {
//            categoryState.addCompetitor(competitor) to listOf(createEvent(command, EventType.COMPETITOR_ADDED, command.payload
//                    ?: emptyMap()))
            listOf(createEvent(command, EventType.COMPETITOR_ADDED, command.payload
                    ?: emptyMap<Any, Any>()))
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
                    ?: emptyMap<Any, Any>()))
//            categoryState.removeCompetitor(competitorId!!) to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, command.payload
//                    ?: emptyMap()))
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, mapOf<String, Any?>(
                    "exception" to "Failed to get competitor id from payload. Or category state is null. (${categoryState == null})",
                    "failedOn" to command)))
        }
    }

    private fun processInitCategoryStateCommand(command: Command): List<EventHolder> {
        val c = getPayloadAs(command.payload?.get("category"), CategoryDescriptor::class.java)
        return if (c != null && command.categoryId != null) {
            val category = c.copy(id = command.categoryId)
            val competition = competitionPropertiesCrudRepository.getOne(command.competitionId)
            val state = CategoryState(command.categoryId, competition, category, CategoryStateStatus.INITIALIZED, null, emptySet())
            listOf(createEvent(command, EventType.CATEGORY_STATE_INITIALIZED, mapOf("categoryState" to state)))
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, mapOf<String, Any?>(
                    "exception" to "Failed to get category from command payload",
                    "failedOn" to command)))
        }
    }

    private fun applyInitCategoryStateEvent(event: EventHolder): Pair<CategoryState, List<EventHolder>> {
        val c = getPayloadAs(event.payload, CategoryStateInitializedPayload::class.java)?.categoryState
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
        val fakeCompetitors = FightsGenerateService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, categoryState!!.category, categoryState.competition.id)
        return fakeCompetitors.map {
            createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(it))
        }
    }

    private val log = LoggerFactory.getLogger(CategoryStateService::class.java)


    private fun <T> getPayloadAs(payload: Any?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.convertValue(payload, clazz)
        }
        return null
    }
}