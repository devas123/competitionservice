package compman.compsrv.service.processor.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.mapping.toDTO
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
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.createEvent
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
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
                CommandType.FIGHTS_EDITOR_APPLY_CHANGE,
                CommandType.GENERATE_BRACKETS_COMMAND,
                CommandType.DELETE_CATEGORY_COMMAND,
                CommandType.CREATE_FAKE_COMPETITORS_COMMAND,
                CommandType.DROP_CATEGORY_BRACKETS_COMMAND)
    }


    override fun executeCommand(command: CommandDTO): List<EventDTO> {
        val events = when (command.type) {
            CommandType.ADD_COMPETITOR_COMMAND -> doAddCompetitor(command)
            CommandType.REMOVE_COMPETITOR_COMMAND -> doRemoveCompetitor(command)
            CommandType.ADD_CATEGORY_COMMAND -> processAddCategoryCommandDTO(command)
            CommandType.UPDATE_COMPETITOR_COMMAND -> doUpdateCompetitor(command)
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND -> doChangeCompetitorCategory(command)
            CommandType.FIGHTS_EDITOR_APPLY_CHANGE -> doApplyFightsEditorChanges(command)
            CommandType.GENERATE_BRACKETS_COMMAND -> doGenerateBrackets(command)
            CommandType.DELETE_CATEGORY_COMMAND -> doDeleteCategoryState(command)
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND -> doCreateFakeCompetitors(command)
            CommandType.DROP_CATEGORY_BRACKETS_COMMAND -> doDropCategoryBrackets(command)
            CommandType.DUMMY_COMMAND -> {
                listOf(createEvent(command, EventType.DUMMY, null))
            }
            else -> {
                log.warn("Unknown command type: ${command.type}")
                listOf(createErrorEvent(command, "Unknown command type: ${command.type}"))
            }
        }
        return events.mapIndexed { _, eventDTO -> eventDTO.setId(IDGenerator.uid()) }
    }

    private fun doDropCategoryBrackets(command: CommandDTO): List<EventDTO> = listOf(createEvent(command, EventType.CATEGORY_BRACKETS_DROPPED, command.payload))

    private fun doUpdateCompetitor(command: CommandDTO): List<EventDTO> {
        val competitor = mapper.convertValue(command.payload, UpdateCompetitorPayload::class.java)?.competitor
        return if (competitor != null && competitorCrudRepository.existsById(competitor.id)) {
            listOf(createEvent(command, EventType.COMPETITOR_UPDATED, CompetitorUpdatedPayload(competitor)))
        } else {
            listOf(createErrorEvent(command, "Competitor is null ${competitor == null} or such competitor does not exist"))
        }
    }

    private fun doChangeCompetitorCategory(command: CommandDTO): List<EventDTO> {
        val payload = mapper.convertValue(command.payload, ChangeCompetitorCategoryPayload::class.java)
        val competitorId = payload?.fighterId
        val newCategoryId = payload?.newCategoryId
        val oldCategoryId = payload?.oldCategoryId
        return if (!newCategoryId.isNullOrBlank() && !competitorId.isNullOrBlank() && competitorCrudRepository.existsById(competitorId) && categoryCrudRepository.existsById(newCategoryId)) {
            val competitor = competitorCrudRepository.findByIdOrNull(competitorId)
            competitor?.let {
                val categories = it.categories?.toSet() ?: emptySet()
                val newCategories = categories.filter { categoryDescriptor -> categoryDescriptor.id != oldCategoryId } + categoryCrudRepository.getOne(newCategoryId)
                listOf(createEvent(command, EventType.COMPETITOR_CATEGORY_CHANGED, CompetitorUpdatedPayload(it.toDTO().setCategories(newCategories.map { it1 -> it1.id }.toTypedArray())))) }
                    ?: listOf(createErrorEvent(command, "Such competitor does not exist"))
        } else {
            listOf(createErrorEvent(command, "New category is null or such competitor does not exist"))
        }
    }


    private fun doApplyFightsEditorChanges(command: CommandDTO): List<EventDTO> {
        if (command.categoryId != null && categoryCrudRepository.existsById(command.categoryId)) {
            val payload = mapper.convertValue(command.payload, FightEditorApplyChangesPayload::class.java)
            val changes = payload.changes
            return if (!changes.isNullOrEmpty()) {
                val newChanges = changes.filter { change ->
                    change.selectedFightIds.all { id -> fightCrudRepository.existsById(id) }
                            && !change.changePatches.isNullOrEmpty()
                            && !change.changeInversePatches.isNullOrEmpty()
                }.toTypedArray()
                listOf(createEvent(command, EventType.FIGHTS_EDITOR_CHANGE_APPLIED, FightEditorChangesAppliedPayload(newChanges)))
            } else {
                listOf(createErrorEvent(command, "Changes list is empty."))
            }
        } else {
            return listOf(createErrorEvent(command, "Category does not exist."))
        }
    }

    private fun createErrorEvent(command: CommandDTO, errorStr: String) = mapper.createErrorEvent(command, errorStr)

    private fun createEvent(command: CommandDTO, eventType: EventType, payload: Any?) = mapper.createEvent(command, eventType, payload)

    private fun doGenerateBrackets(command: CommandDTO): List<EventDTO> {
        val competitors = competitorCrudRepository.findByCompetitionIdAndCategoriesContaining(command.competitionId, setOf(command.categoryId), Pageable.unpaged()).content.toList()
        val payload = mapper.convertValue(command.payload, GenerateBracketsPayload::class.java)
        return if (competitors.isNotEmpty() && fightCrudRepository.findDistinctByCompetitionIdAndCategoryId(command.competitionId, command.categoryId).isNullOrEmpty()) {
            val fights = fightsGenerateService.generateRoundsForCategory(command.categoryId, competitors.toMutableList(), command.competitionId)
            listOf(createEvent(command, EventType.BRACKETS_GENERATED, BracketsGeneratedPayload(fights.map { it.toDTO() }.toTypedArray(), payload?.bracketType
                    ?: BracketType.SINGLE_ELIMINATION)))
        } else {
            listOf(createErrorEvent(command, "Brackets are already generated"))
        }
    }

    private fun doAddCompetitor(command: CommandDTO): List<EventDTO> {
        val competitor = mapper.convertValue(command.payload, CompetitorDTO::class.java)
        val competitorId = IDGenerator.hashString("${command.competitionId}/${command.categoryId}/${competitor?.email}")
        return if (competitor != null && !competitorCrudRepository.existsById(competitorId) && competitor.categories?.contains(command.categoryId) == true && categoryCrudRepository.existsById(command.categoryId)) {
            listOf(createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(competitor.setId(competitorId))))
        } else {
            listOf(createErrorEvent(command, "Failed to get competitor from payload. Or competitor already exists"))
        }
    }

    private fun doRemoveCompetitor(command: CommandDTO): List<EventDTO> {
        val competitorId = mapper.convertValue(command.payload, RemoveCompetitorPayload::class.java)?.competitorId
        return if (!competitorId.isNullOrBlank()) {
            listOf(createEvent(command, EventType.COMPETITOR_REMOVED, CompetitorRemovedPayload(competitorId)))
        } else {
            listOf(createErrorEvent(command,  "Failed to get competitor id from payload."))
        }
    }

    private fun processAddCategoryCommandDTO(command: CommandDTO): List<EventDTO> {
        val c = mapper.convertValue(command.payload, AddCategoryPayload::class.java)?.category
        return if (c != null) {
            val categoryId = command.categoryId ?: IDGenerator.hashString("${command.competitionId}/${IDGenerator.categoryId(c)}")
            if (!categoryCrudRepository.existsById(categoryId) && competitionPropertiesCrudRepository.existsById(command.competitionId)) {
                val state = CategoryStateDTO(categoryId, command.competitionId, c.setId(categoryId), CategoryStatus.INITIALIZED, null, 0, 0, emptyArray())
                listOf(createEvent(command, EventType.CATEGORY_ADDED, CategoryAddedPayload(state)).setCategoryId(categoryId))
            } else {
                listOf(createErrorEvent(command, "Category with ID $categoryId already exists."))
            }
        } else {
            listOf(createErrorEvent(command,"Failed to get category from command payload"))
        }
    }

    private fun doDeleteCategoryState(command: CommandDTO) = listOf(createEvent(command, EventType.CATEGORY_DELETED, command.payload))

    private fun doCreateFakeCompetitors(command: CommandDTO): List<EventDTO> {
        val payload = mapper.convertValue(command.payload, CreateFakeCompetitorsPayload::class.java)
        val numberOfCompetitors = payload?.numberOfCompetitors ?: 50
        val numberOfAcademies = payload?.numberOfAcademies ?: 30
        val categoryState = categoryCrudRepository.getOne(command.categoryId!!)
        val fakeCompetitors = FightsGenerateService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, categoryState.category!!, command.competitionId!!)
        return fakeCompetitors.map {
            createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(it.toDTO()))
        }
    }

    private val log = LoggerFactory.getLogger(CategoryCommandProcessor::class.java)
}