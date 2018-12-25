package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.CategoryState
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.model.dto.competition.CategoryStatus
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.*
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.io.Serializable


@Component
class CategoryStateService constructor(private val fightsGenerateService: FightsGenerateService,
                                       private val mapper: ObjectMapper,
                                       private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                                       private val categoryCrudRepository: CategoryCrudRepository,
                                       private val competitorCrudRepository: CompetitorCrudRepository,
                                       private val fightCrudRepository: FightCrudRepository,
                                       private val bracketsCrudRepository: BracketsCrudRepository) : ICommandProcessingService<CommandDTO, EventDTO> {

    override fun apply(event: EventDTO): List<EventDTO> {
        return when (event.type) {
            EventType.COMPETITOR_ADDED -> applyCompetitorAddedEvent(event)
            EventType.COMPETITOR_REMOVED -> applyCompetitorRemovedEvent(event)
            EventType.COMPETITOR_UPDATED -> applyCompetitorUpdatedEvent(event)
            EventType.COMPETITORS_MOVED -> applyCompetitorMovedEvent(event)
            EventType.BRACKETS_GENERATED -> applyBracketsGeneratedEvent(event)
            EventType.FIGHTS_START_TIME_UPDATED -> applyFighStartTimeUpdatedEvent(event)
            EventType.CATEGORY_DELETED -> applyCategoryStateDeletedEvent(event)
            EventType.CATEGORY_BRACKETS_DROPPED -> applyCategoryBracketsDroppedEvent(event)
            EventType.CATEGORY_ADDED -> applyCategoryAddedEvent(event)
            EventType.DUMMY -> listOf(event)
            else -> {
                log.warn("Unknown event type: ${event.type}")
                emptyList()
            }
        }
    }

    override fun process(command: CommandDTO): List<EventDTO> {
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
                listOf(EventDTO(command.correlationId, command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, ErrorEventPayload(
                        "Unknown command type: ${command.type}",
                        command)))
            }
        }
    }

    private fun applyCategoryBracketsDroppedEvent(event: EventDTO): List<EventDTO> {
        bracketsCrudRepository.deleteById(event.categoryId!!)
        return listOf(event)
    }

    private fun doDropCategoryBrackets(command: CommandDTO): List<EventDTO> = listOf(createEvent(command, EventType.CATEGORY_BRACKETS_DROPPED, command.payload))

    private fun doUpdateCompetitor(command: CommandDTO): List<EventDTO> {
        val competitor = getPayloadAs(command.payload, UpdateCompetitorPayload::class.java)?.competitor
        return if (competitor != null && competitorCrudRepository.existsById(competitor.id)) {
            listOf(createEvent(command, EventType.COMPETITOR_UPDATED, CompetitorUpdatedPayload(competitor)))
        } else {
            listOf(createErrorEvent(command, "Competitor is null ${competitor == null} or such competitor does not exist"))
        }
    }

    private fun applyCompetitorUpdatedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, CompetitorUpdatedPayload::class.java)
        val competitor = payload?.fighter
        return if (competitor != null && competitorCrudRepository.existsById(competitor.id)) {
            competitorCrudRepository.save(Competitor.fromDTO(competitor))
            listOf(event)
        } else {
            throw EventApplyingException("Competitor is null or such competitor does not exist.", event)
        }
    }

    private fun doChangeCompetitorCategory(command: CommandDTO): List<EventDTO> {
        val payload = getPayloadAs(command.payload, ChangeCompetitorCategoryPayload::class.java)
        val competitor = payload?.fighter
        val newCategory = payload?.newCategory
        return if (newCategory != null && competitor != null && competitorCrudRepository.existsById(competitor.id) && categoryCrudRepository.existsById(newCategory.id)) {
            val newCompetitor = competitor.setCategoryId(newCategory.id)
            listOf(createEvent(command, EventType.COMPETITOR_CATEGORY_CHANGED, CompetitorAddedPayload(newCompetitor)))
        } else {
            listOf(createErrorEvent(command, "New category is null ${newCategory == null} or such competitor does not exist"))
        }
    }

    private fun applyCompetitorAddedEvent(event: EventDTO): List<EventDTO> {
        val competitor = getPayloadAs(event.payload, Competitor::class.java)
        return if (competitor != null) {
            competitorCrudRepository.save(competitor)
            listOf(event)
        } else {
            throw EventApplyingException("No competitor in the event payload: $event", event)
        }
    }


    private fun applyCompetitorRemovedEvent(event: EventDTO): List<EventDTO> {
        val competitorId = getPayloadAs(event.payload, CompetitorRemovedPayload::class.java)?.fighterId
        return if (competitorId != null) {
            competitorCrudRepository.deleteById(competitorId)
            listOf(event)
        } else {
            throw EventApplyingException("Competitor id is null.", event)
        }
    }

    private fun applyCompetitorMovedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, CompetitorMovedPayload::class.java)
        val updatedSourceFight = payload?.updatedSourceFight
        val updatedTargetFight = payload?.updatedTargetFight
        return if (updatedSourceFight != null && updatedTargetFight != null && fightCrudRepository.existsById(updatedSourceFight.id) && fightCrudRepository.existsById(updatedTargetFight.id)) {
            fightCrudRepository.saveAll(listOf(FightDescription.fromDTO(updatedSourceFight), FightDescription.fromDTO(updatedTargetFight)))
            listOf(event)
        } else {
            throw EventApplyingException("Source fight or target fight is null or does not exist.", event)
        }
    }

    private fun doMoveCompetitor(command: CommandDTO): List<EventDTO> {
        if (command.categoryId != null && categoryCrudRepository.existsById(command.categoryId)) {
            val payload = getPayloadAs(command.payload, MoveCompetitorPayload::class.java)
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
                            val updatedSourceFight = sourceFight.copy(scores = sourceFight.scores.filter { it.competitor.id != compScorePair.competitor.id }.toTypedArray())
                            val updatedTargetFight = targetFight.setCompetitorWithIndex(compScorePair.competitor, tmpInd)
                            return listOf(createEvent(command, EventType.COMPETITORS_MOVED, CompetitorMovedPayload(updatedSourceFight.toDTO(), updatedTargetFight.toDTO())))
                        }
                        targetFight.scores.size == 2 -> {
                            //need to swap
                            var tmpInd = 1
                            if (index != null && index >= 0 && index < 2) {
                                tmpInd = index
                            }
                            val competitorToSwap = targetFight.scores.drop(tmpInd).first()
                            val updatedSourceFight = sourceFight.copy(scores = ((sourceFight.scores.filter { it.competitor.id != compScorePair.competitor.id }) + competitorToSwap).toTypedArray())
                            val updatedTargetFight = targetFight.setCompetitorWithIndex(compScorePair.competitor, tmpInd)
                            return listOf(createEvent(command, EventType.COMPETITORS_MOVED, CompetitorMovedPayload(updatedSourceFight.toDTO(), updatedTargetFight.toDTO())))
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

    private fun createErrorEvent(command: CommandDTO, errorStr: String) = EventDTO(command.correlationId, command.competitionId, command.categoryId, command.matId, EventType.ERROR_EVENT, ErrorEventPayload(errorStr, command))
    private fun createEvent(command: CommandDTO, eventType: EventType, payload: Serializable?) = EventDTO(command.correlationId, command.competitionId, command.categoryId, command.matId, eventType, payload)

    private fun applyFighStartTimeUpdatedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, FightStartTimeUpdatedPayload::class.java)
        val newFights = payload?.newFights
        return if (newFights != null && newFights.fold(true) { acc: Boolean, fightDescription: FightDescriptionDTO -> acc && fightCrudRepository.existsById(fightDescription.id) }) {
            fightCrudRepository.saveAll(newFights.map { FightDescription.fromDTO(it) })
            listOf(event)
        } else {
            throw EventApplyingException("Fights are null or not all fights are present in the repository.", event)
        }
    }

    private fun applyBracketsGeneratedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, BracketsGeneratedPayload::class.java)
        val fights = payload?.fights
        val bracketType = payload?.bracketType
        val categoryId = event.categoryId
        val compId = event.competitionId
        return if (fights != null && !categoryId.isNullOrBlank()) {
            bracketsCrudRepository.save(BracketDescriptor(categoryId, compId, bracketType
                    ?: BracketType.SINGLE_ELIMINATION, fights.map { FightDescription.fromDTO(it) }.toTypedArray()))
            listOf(event)
        } else {
            throw EventApplyingException("Fights are null or empty or category ID is empty.", event)
        }
    }


    private fun doGenerateBrackets(command: CommandDTO): List<EventDTO> {
        val competitors = competitorCrudRepository.findByCompetitionId(command.competitionId, Pageable.unpaged()).content.toList()
        val payload = getPayloadAs(command.payload, GenerateBracketsPayload::class.java)
        val fights = fightsGenerateService.generatePlayOff(competitors, command.competitionId)
        return listOf(createEvent(command, EventType.BRACKETS_GENERATED, BracketsGeneratedPayload(fights.map { it.toDTO() }.toTypedArray(), payload?.bracketType
                ?: BracketType.SINGLE_ELIMINATION)))
    }

    private fun doAddCompetitor(command: CommandDTO): List<EventDTO> {
        val competitor = getPayloadAs(command.payload, CompetitorDTO::class.java)
        return if (competitor != null && !competitorCrudRepository.existsById(competitor.id)) {
//            categoryState.addCompetitor(competitor) to listOf(createEvent(command, EventType.COMPETITOR_ADDED, command.payload
//                    ?: emptyMap()))
            listOf(createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(competitor)))
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, ErrorEventPayload("Failed to get competitor from payload. Or competitor already exists", command)))
        }
    }

    private fun doRemoveCompetitor(command: CommandDTO): List<EventDTO> {
        val competitorId = getPayloadAs(command.payload, RemoveCompetitorPayload::class.java)?.competitorId
        return if (!competitorId.isNullOrBlank()) {
            listOf(createEvent(command, EventType.COMPETITOR_REMOVED, CompetitorRemovedPayload(competitorId)))
//            categoryState.removeCompetitor(competitorId!!) to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, command.payload
//                    ?: emptyMap()))
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, ErrorEventPayload("Failed to get competitor id from payload.", command)))
        }
    }

    private fun processAddCategoryCommandDTO(command: CommandDTO): List<EventDTO> {
        val c = getPayloadAs(command.payload, AddCategoryPayload::class.java)?.category
        return if (c != null && command.categoryId != null) {
            val competition = competitionPropertiesCrudRepository.getOne(command.competitionId)
            val state = CategoryStateDTO(command.categoryId, competition.id, c, CategoryStatus.INITIALIZED, null, emptyArray())
            listOf(createEvent(command, EventType.CATEGORY_ADDED, CategoryAddedPayload(state)))
        } else {
            listOf(createEvent(command, EventType.ERROR_EVENT, ErrorEventPayload("Failed to get category from command payload", command)))
        }
    }

    private fun applyCategoryAddedEvent(event: EventDTO): List<EventDTO> {
        val c = getPayloadAs(event.payload, CategoryAddedPayload::class.java)?.categoryState
        val props = competitionPropertiesCrudRepository.findById(event.competitionId)
        return if (c != null && event.categoryId != null && props.isPresent) {
            categoryCrudRepository.save(CategoryState.fromDTO(c, props.get()))
            listOf(event)
        } else {
            throw EventApplyingException("event did not contain category state.", event)
        }
    }

    private fun doDeleteCategoryState(command: CommandDTO) = listOf(createEvent(command, EventType.CATEGORY_DELETED, command.payload))
    private fun applyCategoryStateDeletedEvent(event: EventDTO): List<EventDTO> {
        return if (!event.categoryId.isNullOrBlank()) {
            categoryCrudRepository.deleteById(event.categoryId)
            listOf(event)
        } else {
            throw EventApplyingException("Category ID is null.", event)
        }
    }


    private fun doCreateFakeCompetitors(command: CommandDTO): List<EventDTO> {
        val payload = getPayloadAs(command.payload, CreateFakeCompetitorsPayload::class.java)
        val numberOfCompetitors = payload?.numberOfCompetitors ?: 50
        val numberOfAcademies = payload?.numberOfAcademies ?: 30
        val categoryState = categoryCrudRepository.getOne(command.categoryId!!)
        val fakeCompetitors = FightsGenerateService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, categoryState.category, categoryState.competition.id)
        return fakeCompetitors.map {
            createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(it.toDTO()))
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