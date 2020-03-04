package compman.compsrv.service.processor.command

import arrow.core.fix
import com.compmanager.compservice.jooq.tables.CategoryDescriptor
import com.compmanager.compservice.jooq.tables.daos.CategoryDescriptorDao
import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import com.compmanager.compservice.jooq.tables.daos.CompetitorDao
import com.compmanager.compservice.jooq.tables.daos.FightDescriptionDao
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.brackets.StageResultDescriptorDTO
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.brackets.StageType
import compman.compsrv.model.dto.competition.CategoryRestrictionDTO
import compman.compsrv.model.dto.competition.CategoryStateDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.JooqQueryProvider
import compman.compsrv.repository.JooqRepository
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.service.fight.FightsService
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.Rules
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.createEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*


@Component
class CategoryCommandProcessor constructor(private val fightsGenerateService: FightServiceFactory,
                                           private val mapper: ObjectMapper,
                                           private val competitionPropertiesCrudRepository: CompetitionPropertiesDao,
                                           private val categoryCrudRepository: CategoryDescriptorDao,
                                           private val competitorCrudRepository: CompetitorDao,
                                           private val fightCrudRepository: FightDescriptionDao,
                                           private val jooq: JooqRepository,
                                           private val jooqQueryProvider: JooqQueryProvider
) : ICommandProcessor {
    private val commandsToHandlers: Map<CommandType, (command: CommandDTO) -> List<EventDTO>> = setOf(CommandType.ADD_COMPETITOR_COMMAND to ::doAddCompetitor,
            CommandType.REMOVE_COMPETITOR_COMMAND to ::doRemoveCompetitor,
            CommandType.ADD_CATEGORY_COMMAND to ::processAddCategoryCommandDTO,
            CommandType.UPDATE_COMPETITOR_COMMAND to ::doUpdateCompetitor,
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND to ::doChangeCompetitorCategory,
            CommandType.FIGHTS_EDITOR_APPLY_CHANGE to ::doApplyFightsEditorChanges,
            CommandType.GENERATE_BRACKETS_COMMAND to ::doGenerateBrackets,
            CommandType.DELETE_CATEGORY_COMMAND to ::doDeleteCategoryState,
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND to ::doCreateFakeCompetitors,
            CommandType.CHANGE_CATEGORY_REGISTRATION_STATUS_COMMAND to ::doChangeCategoryRegistrationStatus,
            CommandType.DROP_CATEGORY_BRACKETS_COMMAND to ::doDropCategoryBrackets).toMap()

    override fun affectedCommands(): Set<CommandType> {
        return commandsToHandlers.keys
    }


    override fun executeCommand(command: CommandDTO): List<EventDTO> {
        val events = commandsToHandlers[command.type]?.invoke(command) ?: run {
            log.warn("Unknown command type: ${command.type}")
            listOf(createErrorEvent(command, "Unknown command type: ${command.type}"))
        }
        return events.mapIndexed { _, eventDTO -> eventDTO.setId(IDGenerator.uid()) }
    }

    private fun doChangeCategoryRegistrationStatus(command: CommandDTO): List<EventDTO> {
        return if (categoryCrudRepository.existsById(command.categoryId)) {
            listOf(createEvent(command, EventType.CATEGORY_REGISTRATION_STATUS_CHANGED, command.payload))
        } else {
            listOf(createErrorEvent(command, "No category with id ${command.categoryId}"))
        }
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
        return if (!newCategoryId.isNullOrBlank() && !competitorId.isNullOrBlank() && competitorCrudRepository.existsById(competitorId) && categoryCrudRepository.existsById(newCategoryId)) {
            val competitor = competitorCrudRepository.findById(competitorId)
            competitor?.let {
                listOf(createEvent(command, EventType.COMPETITOR_CATEGORY_CHANGED, payload))
            }
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
        val competitors = jooqQueryProvider.competitorsQuery(command.competitionId).and(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.eq(command.categoryId)).fetch { rec -> jooq.mapCompetitorWithoutCategories(rec) }
        val payload = mapper.convertValue(command.payload, GenerateBracketsPayload::class.java)
        return if (!competitors.isNullOrEmpty()
                && !payload?.stageDescriptors.isNullOrEmpty()) {
            val duration = categoryCrudRepository.findById(command.categoryId).fightDuration!!
            val stages = payload.stageDescriptors.sortedBy { it.stageOrder }
            val updatedStages = stages.mapIndexed { ind, stage ->
                val outputSize = when (stage.stageType) {
                    StageType.PRELIMINARY -> stages[ind + 1].inputDescriptor.numberOfCompetitors!!
                    else -> 0
                }
                val stageId = IDGenerator.stageId(command.competitionId, command.categoryId, stage.name, stage.stageOrder)
                val groupDescr = stage.groupDescriptors?.mapIndexed { index, it ->
                    it.setId(IDGenerator.groupId(command.competitionId, command.categoryId, stageId, index))
                }?.toTypedArray()
                val inputDescriptor = stage.inputDescriptor?.setId(stageId)?.setSelectors(stage.inputDescriptor?.selectors?.mapIndexed { index, sel -> sel.setId("$stageId-s-$index") }?.toTypedArray())
                val resultDescriptor = stage.stageResultDescriptor?.setId(stageId) ?: StageResultDescriptorDTO().setId(stageId)
                val stageWithIds = stage
                        .setCategoryId(command.categoryId)
                        .setId(stageId)
                        .setCompetitionId(command.competitionId)
                        .setGroupDescriptors(groupDescr)
                        .setInputDescriptor(inputDescriptor)
                        .setStageResultDescriptor(resultDescriptor)
                val size = if (stage.stageOrder == 0) competitors.size else stage.inputDescriptor.numberOfCompetitors!!
                val status = if (stage.stageOrder == 0) StageStatus.WAITING_FOR_APPROVAL else StageStatus.WAITING_FOR_COMPETITORS
                val comps = if (stage.stageOrder == 0) { competitors } else { emptyList() }
                val twoFighterFights = fightsGenerateService.generateStageFights(command.competitionId, command.categoryId,
                        stageWithIds,
                        size, duration, comps, outputSize)
                stageWithIds
                        .setName(stageWithIds.name ?: "Default brackets")
                        .setStageStatus(status)
                        .setInputDescriptor(inputDescriptor)
                        .setStageResultDescriptor(stageWithIds.stageResultDescriptor
                                ?.setFightResultOptions(stageWithIds.stageResultDescriptor?.fightResultOptions?.map { it.setId(IDGenerator.hashString("$stageId-${UUID.randomUUID()}")) }?.toTypedArray()))
                        .setNumberOfFights(twoFighterFights.size) to twoFighterFights
            }
            val fightAddedEvents = updatedStages.flatMap { pair ->
                pair.second.chunked(30).map {
                    createEvent(command, EventType.FIGHTS_ADDED_TO_STAGE,
                            FightsAddedToStagePayload(it.toTypedArray(), pair.first.id))
                }
            }
            listOf(createEvent(command, EventType.BRACKETS_GENERATED, BracketsGeneratedPayload(updatedStages.mapNotNull { it.first }.toTypedArray()))) + fightAddedEvents

        } else {
            listOf(createErrorEvent(command, "Category is empty or no stage description provided."))
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
            if (jooq.fightsCount(command.competitionId, competitorId) <= 0) {
                listOf(createEvent(command, EventType.COMPETITOR_REMOVED, CompetitorRemovedPayload(competitorId)))
            } else {
                listOf(createErrorEvent(command, "There are already fights generated for the competitor. Please remove the fights first."))
            }
        } else {
            listOf(createErrorEvent(command, "Failed to get competitor id from payload."))
        }
    }

    private fun CategoryRestrictionDTO.withId(): CategoryRestrictionDTO = this.setId(IDGenerator.restrictionId(this))

    private fun processAddCategoryCommandDTO(command: CommandDTO): List<EventDTO> {
        val c = mapper.convertValue(command.payload, AddCategoryPayload::class.java)?.category
        return if (c != null && !c.restrictions.isNullOrEmpty()) {
            val restrictionsValid = Rules.accumulateErrors {
                c.restrictions.map { it.validate() }
            }.map { it.fix() }
            val categoryId = command.categoryId
                    ?: IDGenerator.hashString("${command.competitionId}/${IDGenerator.categoryId(c)}")
            if (!categoryCrudRepository.existsById(categoryId) && competitionPropertiesCrudRepository.existsById(command.competitionId)) {
                if (restrictionsValid.all { restriction -> restriction.isValid }) {
                    val registrationOpen = c.registrationOpen ?: true
                    val state = CategoryStateDTO().setId(categoryId).setCompetitionId(command.competitionId).setCategory(c
                            .setRestrictions(c.restrictions.map { it.withId() }.toTypedArray())
                            .setId(categoryId).setRegistrationOpen(registrationOpen)).setFightsNumber(0)
                            .setNumberOfCompetitors(0)

                    listOf(createEvent(command, EventType.CATEGORY_ADDED, CategoryAddedPayload(state)).setCategoryId(categoryId))
                } else {
                    listOf(createErrorEvent(command, restrictionsValid.fold(StringBuilder()) { acc, r -> acc.append(r.fold({ it.toList().joinToString(",") }, { "" })) }.toString()))
                }
            } else {
                listOf(createErrorEvent(command, "Category with ID $categoryId already exists."))
            }
        } else {
            listOf(createErrorEvent(command, "Failed to get category from command payload"))
        }
    }

    private fun doDeleteCategoryState(command: CommandDTO): List<EventDTO> {
        return if (jooq.competitorsCount(command.competitionId, command.categoryId) == 0) {
            listOf(createEvent(command, EventType.CATEGORY_DELETED, command.payload))
        } else {
            listOf(createErrorEvent(command, "There are already competitors registered to this category. Please move them to another category first."))
        }
    }

    private fun doCreateFakeCompetitors(command: CommandDTO): List<EventDTO> {
        val payload = mapper.convertValue(command.payload, CreateFakeCompetitorsPayload::class.java)
        val numberOfCompetitors = payload?.numberOfCompetitors ?: 50
        val numberOfAcademies = payload?.numberOfAcademies ?: 30
        val categoryState = jooq.fetchCategoryStateByCompetitionIdAndCategoryId(command.competitionId, command.categoryId!!).block()
        val fakeCompetitors = FightsService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, categoryState?.category!!, command.competitionId!!)
        return fakeCompetitors.map {
            createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(it))
        }
    }

    private val log = LoggerFactory.getLogger(CategoryCommandProcessor::class.java)
}