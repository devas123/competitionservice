package compman.compsrv.service.processor.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.model.dto.competition.CategoryStatus
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.CategoryStateCrudRepository
import compman.compsrv.repository.CompetitionPropertiesCrudRepository
import compman.compsrv.repository.CompetitorCrudRepository
import compman.compsrv.repository.FightCrudRepository
import compman.compsrv.service.FightsGenerateService
import compman.compsrv.util.IDGenerator
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component


@Component
class CategoryCommandProcessor constructor(private val fightsGenerateService: FightsGenerateService,
                                           private val mapper: ObjectMapper,
                                           private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                                           private val categoryCrudRepository: CategoryStateCrudRepository,
                                           private val competitorCrudRepository: CompetitorCrudRepository,
                                           private val fightCrudRepository: FightCrudRepository) : ICommandProcessor {

    override fun affectedCommands(): Set<CommandType> {
        return setOf(CommandType.ADD_COMPETITOR_COMMAND,
                CommandType.REMOVE_COMPETITOR_COMMAND,
                CommandType.ADD_CATEGORY_COMMAND,
                CommandType.UPDATE_COMPETITOR_COMMAND,
                CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND,
                CommandType.CHANGE_COMPETITOR_FIGHT_COMMAND,
                CommandType.GENERATE_BRACKETS_COMMAND,
                CommandType.DELETE_CATEGORY_COMMAND,
                CommandType.CREATE_FAKE_COMPETITORS_COMMAND,
                CommandType.DROP_CATEGORY_BRACKETS_COMMAND)
    }


    override fun executeCommand(command: CommandDTO): List<EventDTO> {
        return when (command.type) {
            CommandType.ADD_COMPETITOR_COMMAND -> doAddCompetitor(command)
            CommandType.REMOVE_COMPETITOR_COMMAND -> doRemoveCompetitor(command)
            CommandType.ADD_CATEGORY_COMMAND -> processAddCategoryCommandDTO(command)
            CommandType.UPDATE_COMPETITOR_COMMAND -> doUpdateCompetitor(command)
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> doChangeCompetitorCategory(command)
            CommandType.CHANGE_COMPETITOR_FIGHT_COMMAND -> doMoveCompetitor(command)
            CommandType.GENERATE_BRACKETS_COMMAND -> doGenerateBrackets(command)
            CommandType.DELETE_CATEGORY_COMMAND -> doDeleteCategoryState(command)
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND -> doCreateFakeCompetitors(command)
            CommandType.DROP_CATEGORY_BRACKETS_COMMAND -> doDropCategoryBrackets(command)
            CommandType.DUMMY_COMMAND -> {
                listOf(createEvent(command, EventType.DUMMY, null))
            }
            else -> {
                log.warn("Unknown command type: ${command.type}")
                listOf(EventDTO()
                        .setCategoryId(command.categoryId)
                        .setCorrelationId(command.correlationId)
                        .setCompetitionId(command.competitionId)
                        .setMatId(command.matId)
                        .setType(EventType.ERROR_EVENT)
                        .setPayload(mapper.writeValueAsString(ErrorEventPayload("Unknown command type: ${command.type}", command.correlationId))))
            }
        }
    }

    private fun doDropCategoryBrackets(command: CommandDTO): List<EventDTO> = listOf(createEvent(command, EventType.CATEGORY_BRACKETS_DROPPED, mapper.writeValueAsString(command.payload)))

    private fun doUpdateCompetitor(command: CommandDTO): List<EventDTO> {
        val competitor = mapper.convertValue(command.payload, UpdateCompetitorPayload::class.java)?.competitor
        return if (competitor != null && competitorCrudRepository.existsById(competitor.id)) {
            listOf(createEvent(command, EventType.COMPETITOR_UPDATED, mapper.writeValueAsString(CompetitorUpdatedPayload(competitor))))
        } else {
            listOf(createErrorEvent(command, "Competitor is null ${competitor == null} or such competitor does not exist"))
        }
    }

    private fun doChangeCompetitorCategory(command: CommandDTO): List<EventDTO> {
        val payload = mapper.convertValue(command.payload, ChangeCompetitorCategoryPayload::class.java)
        val competitor = payload?.fighter
        val newCategory = payload?.newCategory
        return if (newCategory != null && competitor != null && competitorCrudRepository.existsById(competitor.id) && categoryCrudRepository.existsById(newCategory.id)) {
            val newCompetitor = competitor.setCategoryId(newCategory.id)
            listOf(createEvent(command, EventType.COMPETITOR_CATEGORY_CHANGED, mapper.writeValueAsString(CompetitorUpdatedPayload(newCompetitor))))
        } else {
            listOf(createErrorEvent(command, "New category is null ${newCategory == null} or such competitor does not exist"))
        }
    }


    private fun doMoveCompetitor(command: CommandDTO): List<EventDTO> {
        if (command.categoryId != null && categoryCrudRepository.existsById(command.categoryId)) {
            val payload = mapper.convertValue(command.payload, MoveCompetitorPayload::class.java)
            val competitorId = payload?.competitorId
            val fromFightId = payload?.sourceFightId
            val toFightId = payload?.targetFightId
            val index = payload?.index
            if (competitorId.isNullOrBlank() || fromFightId.isNullOrBlank() || toFightId.isNullOrBlank() || !fightCrudRepository.existsById(fromFightId) || !fightCrudRepository.existsById(toFightId)) {
                return listOf(createErrorEvent(command, "competitor ID or source fight ID or target fight ID is null."))
            }
            val sourceFight = fightCrudRepository.findById(fromFightId).orElse(null)
            val targetFight = fightCrudRepository.findById(toFightId).orElse(null)
            if (sourceFight != null && targetFight != null) {
                val compScorePair = sourceFight.scores.find { it.competitor.id == competitorId }
                if (compScorePair != null) {
                    when {
                        targetFight.scores.size < 2 -> {
                            var tmpInd = 1
                            if (index != null && index >= 0 && index < 2) {
                                tmpInd = index
                            }
                            sourceFight.scores = sourceFight.scores.filter { it.competitor.id != compScorePair.competitor.id }.toTypedArray()
                            val updatedTargetFight = targetFight.setCompetitorWithIndex(compScorePair.competitor, tmpInd)
                            return listOf(createEvent(command, EventType.COMPETITORS_MOVED, mapper.writeValueAsString(CompetitorMovedPayload(sourceFight.toDTO(), updatedTargetFight.toDTO()))))
                        }
                        targetFight.scores.size == 2 -> {
                            //need to swap
                            var tmpInd = 1
                            if (index != null && index >= 0 && index < 2) {
                                tmpInd = index
                            }
                            val competitorToSwap = targetFight.scores.drop(tmpInd).first()
                            sourceFight.scores = ((sourceFight.scores.filter { it.competitor.id != compScorePair.competitor.id }) + competitorToSwap).toTypedArray()
                            val updatedTargetFight = targetFight.setCompetitorWithIndex(compScorePair.competitor, tmpInd)
                            return listOf(createEvent(command, EventType.COMPETITORS_MOVED, mapper.writeValueAsString(CompetitorMovedPayload(sourceFight.toDTO(), updatedTargetFight.toDTO()))))
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
            return listOf(createErrorEvent(command, "Category does not exist."))
        }
    }

    private fun createErrorEvent(command: CommandDTO, errorStr: String) =
            EventDTO()
                    .setCategoryId(command.categoryId)
                    .setCorrelationId(command.correlationId)
                    .setCompetitionId(command.competitionId)
                    .setMatId(command.matId)
                    .setType(EventType.ERROR_EVENT)
                    .setPayload(mapper.writeValueAsString(ErrorEventPayload(errorStr, command.correlationId)))

    private fun createEvent(command: CommandDTO, eventType: EventType, payload: String?) =
            EventDTO()
                    .setCategoryId(command.categoryId)
                    .setCorrelationId(command.correlationId)
                    .setCompetitionId(command.competitionId)
                    .setMatId(command.matId)
                    .setType(eventType)
                    .setPayload(payload)


    private fun doGenerateBrackets(command: CommandDTO): List<EventDTO> {
        val competitors = competitorCrudRepository.findByCompetitionIdAndCategoryId(command.competitionId, command.categoryId, Pageable.unpaged()).content.toList()
        val payload = mapper.convertValue(command.payload, GenerateBracketsPayload::class.java)
        return if (competitors.isNotEmpty() && fightCrudRepository.findByCompetitionIdAndCategoryId(command.competitionId, command.categoryId).isNullOrEmpty()) {
            val fights = fightsGenerateService.generateRoundsForCategory(command.categoryId, competitors.toMutableList(), command.competitionId)
            listOf(createEvent(command, EventType.BRACKETS_GENERATED, mapper.writeValueAsString(BracketsGeneratedPayload(fights.map { it.toDTO() }.toTypedArray(), payload?.bracketType
                    ?: BracketType.SINGLE_ELIMINATION))))
        } else {
            listOf(createErrorEvent(command, "Brackets already generated"))
        }
    }

    private fun doAddCompetitor(command: CommandDTO): List<EventDTO> {
        val competitor = mapper.convertValue(command.payload, CompetitorDTO::class.java)
        val competitorId = IDGenerator.hashString("${command.competitionId}/${command.categoryId}/${competitor?.email}")
        return if (competitor != null && !competitorCrudRepository.existsById(competitorId) && command.categoryId == competitor.categoryId && categoryCrudRepository.existsById(command.categoryId)) {
//            categoryState.addCompetitor(competitor) to listOf(createEvent(command, EventType.COMPETITOR_ADDED, command.payload
//                    ?: emptyMap()))
            listOf(createEvent(command, EventType.COMPETITOR_ADDED, mapper.writeValueAsString(CompetitorAddedPayload(competitor.setId(competitorId)))))
        } else {
            listOf(createErrorEvent(command,"Failed to get competitor from payload. Or competitor already exists"))
        }
    }

    private fun doRemoveCompetitor(command: CommandDTO): List<EventDTO> {
        val competitorId = mapper.convertValue(command.payload, RemoveCompetitorPayload::class.java)?.competitorId
        return if (!competitorId.isNullOrBlank()) {
            listOf(createEvent(command, EventType.COMPETITOR_REMOVED, mapper.writeValueAsString(CompetitorRemovedPayload(competitorId))))
//            categoryState.removeCompetitor(competitorId!!) to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, command.payload
//                    ?: emptyMap()))
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, mapper.writeValueAsString(ErrorEventPayload("Failed to get competitor id from payload.", command.correlationId))))
        }
    }

    private fun processAddCategoryCommandDTO(command: CommandDTO): List<EventDTO> {
        val c = mapper.convertValue(command.payload, AddCategoryPayload::class.java)?.category
        return if (c != null) {
            val categoryId = IDGenerator.hashString("${command.competitionId}/${c.gender}/${c.ageDivision?.id}/${c.weight?.id}/${c.beltType}")
            if (!categoryCrudRepository.existsById(categoryId)) {
                val competition = competitionPropertiesCrudRepository.getOne(command.competitionId)
                val state = CategoryStateDTO(categoryId, competition.id, c.setId(categoryId), CategoryStatus.INITIALIZED, null, 0, 0, emptyArray())
                listOf(createEvent(command, EventType.CATEGORY_ADDED, mapper.writeValueAsString(CategoryAddedPayload(state))).setCategoryId(categoryId))
            } else {
                listOf(createErrorEvent(command, "Category with ID $categoryId already exists."))
            }
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, mapper.writeValueAsString(ErrorEventPayload("Failed to get category from command payload", command.correlationId))))
        }
    }

    private fun doDeleteCategoryState(command: CommandDTO) = listOf(createEvent(command, EventType.CATEGORY_DELETED, mapper.writeValueAsString(command.payload)))

    private fun doCreateFakeCompetitors(command: CommandDTO): List<EventDTO> {
        val payload = mapper.convertValue(command.payload, CreateFakeCompetitorsPayload::class.java)
        val numberOfCompetitors = payload?.numberOfCompetitors ?: 50
        val numberOfAcademies = payload?.numberOfAcademies ?: 30
        val categoryState = categoryCrudRepository.getOne(command.categoryId!!)
        val fakeCompetitors = FightsGenerateService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, categoryState.category!!, categoryState.competition?.id!!)
        return fakeCompetitors.map {
            createEvent(command, EventType.COMPETITOR_ADDED, mapper.writeValueAsString(CompetitorAddedPayload(it.toDTO())))
        }
    }

    private val log = LoggerFactory.getLogger(CategoryCommandProcessor::class.java)
}