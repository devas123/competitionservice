package compman.compsrv.service.processor.command

import arrow.core.Tuple2
import arrow.core.fix
import com.compmanager.compservice.jooq.tables.CategoryDescriptor
import com.compmanager.compservice.jooq.tables.daos.CategoryDescriptorDao
import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import com.compmanager.compservice.jooq.tables.daos.CompetitorDao
import com.compmanager.compservice.jooq.tables.daos.StageDescriptorDao
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.mapping.toPojo
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.JooqMappers
import compman.compsrv.repository.JooqQueryProvider
import compman.compsrv.repository.JooqRepository
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.fight.FightsService.Companion.createEmptyScore
import compman.compsrv.service.fight.GroupStageGenerateService
import compman.compsrv.util.*
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.util.*


@Component
class CategoryCommandProcessor constructor(private val fightsGenerateService: FightServiceFactory,
                                           mapper: ObjectMapper,
                                           validators: List<PayloadValidator>,
                                           private val competitionPropertiesCrudRepository: CompetitionPropertiesDao,
                                           private val categoryCrudRepository: CategoryDescriptorDao,
                                           private val stageDescriptorDao: StageDescriptorDao,
                                           private val competitorCrudRepository: CompetitorDao,
                                           private val jooq: JooqRepository,
                                           private val jooqQueryProvider: JooqQueryProvider,
                                           private val jooqMappers: JooqMappers
) : AbstractCommandProcessor(mapper, validators) {

    companion object {
        val changePriority = GroupChangeType.values().associate {
            it to when (it) {
                GroupChangeType.REMOVE -> 0
                GroupChangeType.ADD -> 1
                else -> Int.MAX_VALUE
            }
        }.withDefault { Int.MAX_VALUE }
    }


    private val commandsToHandlers: Map<CommandType, (command: CommandDTO) -> List<EventDTO>> = setOf(
            CommandType.ADD_COMPETITOR_COMMAND to ::doAddCompetitor,
            CommandType.UPDATE_STAGE_STATUS_COMMAND to ::updateStageStatus,
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

    private final tailrec fun clearAffectedFights(fights: List<FightDescriptionDTO>, changedIds: Set<String>): List<FightDescriptionDTO> {
        return if (changedIds.isEmpty()) {
            fights
        } else {
            val affectedFights = fights.filter { fg -> fg.scores?.any { s -> changedIds.contains(s.parentFightId) } == true }
            clearAffectedFights(fights.map { f -> f.setScores(f.scores?.applyConditionalUpdate({ changedIds.contains(it.parentFightId) }, { it.setCompetitorId(null) })) }, affectedFights.map { it.id }.toSet())
        }
    }


    private fun doApplyFightsEditorChanges(com: CommandDTO): List<EventDTO> {
        return executeValidated(com, FightEditorApplyChangesPayload::class.java) { payload, command ->
            if (categoryCrudRepository.existsById(command.categoryId)) {
                val competitionProperties = competitionPropertiesCrudRepository.findById(command.competitionId)
                val stage = jooq.fetchStageById(command.competitionId, payload.stageId).block(Duration.ofMillis(300))!!
                val bracketsType = stage.bracketType
                if (!competitionProperties.schedulePublished && !competitionProperties.bracketsPublished) {
                    val allStageFights = jooq.fetchFightsByStageId(command.competitionId, payload.stageId).collectList().block(Duration.ofMillis(300)).orEmpty()
                    val stageFights = when (bracketsType) {
                        BracketType.GROUP -> {
                            val fightsByGroupId = allStageFights.groupBy { it.groupId!! }
                            val competitorChangesByGroupId = payload.competitorGroupChanges.groupBy { it.groupId }
                            val groups = stage.groupDescriptors
                            groups.flatMap { gr ->
                                val groupChanges = competitorChangesByGroupId[gr.id].orEmpty()
                                val groupFights = fightsByGroupId[gr.id].orEmpty()
                                groupChanges.sortedBy { changePriority[it.changeType] }.fold(groupFights) { acc, ch ->
                                    when (ch.changeType!!) {
                                        GroupChangeType.ADD -> {
                                            createUpdatesWithAddedCompetitor(acc, ch, payload.stageId, command.competitionId, command.categoryId)
                                        }
                                        GroupChangeType.REMOVE -> {
                                            createUpdatesWithRemovedCompetitor(acc, ch, gr)
                                        }
                                    }
                                }
                            }.filterNotNull().sortedBy { it.numberInRound }.mapIndexed { i, fightDescriptionDTO -> fightDescriptionDTO.setNumberInRound(i) }
                        }
                        else -> {
                            allStageFights.map { f ->
                                payload.bracketsChanges.find { change -> change.fightId == f.id }?.let { change ->
                                    if (change.competitors.isNullOrEmpty()) {
                                        f.setScores(emptyArray())
                                    } else {
                                        val scores = f.scores.orEmpty()
                                        f.setScores(change.competitors.mapIndexed { index, cmpId ->
                                            scores.find { s -> s.competitorId == cmpId }
                                                    ?: scores.find { s -> s.competitorId.isNullOrBlank() }?.setCompetitorId(cmpId)
                                                    ?: CompScoreDTO()
                                                            .setCompetitorId(cmpId)
                                                            .setScore(ScoreDTO(0, 0, 0, emptyArray()))
                                                            .setOrder(getMinUnusedOrder(scores, index))
                                        }.toTypedArray())
                                    }
                                } ?: f
                            }
                        }
                    }

                    val dirtyStageFights = stageFights.applyConditionalUpdate({ it.status == FightStatus.UNCOMPLETABLE }, { it.setStatus(FightStatus.PENDING) })

                    val clearedStageFights = clearAffectedFights(dirtyStageFights, payload.bracketsChanges.map { it.fightId }.toSet())

                    val markedStageFights = FightsService.markAndProcessUncompletableFights(clearedStageFights, stage.stageStatus) { id ->
                        (allStageFights.firstOrNull { it.id == id }
                                ?: stageFights.firstOrNull { it.id == id })?.scores?.map { it.toPojo(id) }
                    }


                    listOf(createEvent(command, EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
                            FightEditorChangesAppliedPayload()
                                    .setRemovedFighids(allStageFights.filter { asf -> markedStageFights.none { msf -> asf.id == msf.id } }
                                            .map { it.id }.toTypedArray())
                                    .setUpdates(markedStageFights.filter { allStageFights.any { asf -> asf.id == it.id } }
                                            .toTypedArray())
                                    .setNewFights(markedStageFights.filter { allStageFights.none { asf -> asf.id == it.id } }.toTypedArray())))
                } else {
                    listOf(createErrorEvent(command, "Competition schedule or brackets are published."))
                }
            } else {
                listOf(createErrorEvent(command, "Category does not exist."))
            }
        }
    }

    private fun getMinUnusedOrder(scores: Array<out CompScoreDTO>?, index: Int = 0): Int {
        return if (scores.isNullOrEmpty()) {
            0
        } else {
            (0..scores.size + index).filter { i -> scores.none { s -> s.order == i } }[index]
        }
    }


    private fun createUpdatesWithRemovedCompetitor(groupFights: List<FightDescriptionDTO>, ch: CompetitorGroupChange, groupDescriptorDTO: GroupDescriptorDTO): List<FightDescriptionDTO> {
        val actualGroupSize = groupFights.flatMap { it.scores.orEmpty().toList() }.distinctBy {
            it.competitorId ?: it.placeholderId
        }.size
        return if (actualGroupSize <= groupDescriptorDTO.size) {
            groupFights.map {
                it.setScores(it.scores?.map { sc ->
                    if (sc.competitorId == ch.competitorId) {
                        sc.setCompetitorId(null).setScore(createEmptyScore())
                    } else {
                        sc
                    }
                }?.toTypedArray())
            }
        } else {
            groupFights.filter {
                it.scores?.none { sc -> sc.competitorId == ch.competitorId } ?: true
            }
        }
    }


    private fun createUpdatesWithAddedCompetitor(groupFights: List<FightDescriptionDTO>, ch: CompetitorGroupChange, stageId: String,
                                                 competitionId: String, categoryId: String): List<FightDescriptionDTO> {
        if (groupFights.any { f -> f.scores.any { it.competitorId == ch.competitorId } }) {
            log.info("Group already contains fights for competitor ${ch.competitorId}. Assuming the competitor is already added to the group.")
            return groupFights
        }
        //find placeholder in existing fights.
        val flatScores = groupFights.flatMap { it.scores.orEmpty().toList() }
        val placeholderId = flatScores
                .find { it.competitorId.isNullOrBlank() && !it.placeholderId.isNullOrBlank() }?.placeholderId
        return if (!placeholderId.isNullOrBlank()) {
            log.info("Found placeholder: $placeholderId")
            groupFights.map { fight ->
                fight.setScores(fight.scores?.map {
                    if (it.placeholderId == placeholderId) {
                        log.info("Updating fight with compscores: ${it.competitorId}/${it.placeholderId}, setting competitorId to ${ch.competitorId}")
                        it.setCompetitorId(ch.competitorId)
                    } else {
                        it
                    }
                }?.toTypedArray())
            }
        } else {
            log.info("Did not find placeholder, creating new fight.")
            val groupCompetitors = flatScores.map { it.competitorId }.distinct()
            val newCompetitorPairs = GroupStageGenerateService.createPairs(groupCompetitors, listOf(ch.competitorId))
                    .filter<Tuple2<String, String>> { it.a != it.b }.distinctBy { sortedSetOf<String?>(it.a, it.b).joinToString() }
            val startIndex = (groupFights.maxBy { it.numberInRound }?.numberInRound
                    ?: 0) + 1
            val duration = groupFights.first().duration
            val newPlaceholderId = (flatScores.map { it.competitorId to it.placeholderId } + (ch.competitorId to "placeholder-${UUID.randomUUID()}")).toMap<String?, String?>()
            val newFights = newCompetitorPairs.mapIndexed { index, tuple2 ->
                FightsService.fightDescription(competitionId, categoryId,
                        stageId, 0,
                        StageRoundType.GROUP, startIndex + index,
                        duration, "Round 0 fight ${startIndex + index}", ch.groupId)
                        .setScores(arrayOf(
                                GroupStageGenerateService.createCompscore(tuple2.a, newPlaceholderId[tuple2.a], 0),
                                GroupStageGenerateService.createCompscore(tuple2.b, newPlaceholderId[tuple2.b], 1)))
            }
            groupFights + newFights
        }
    }

    private fun createErrorEvent(command: CommandDTO, errorStr: String) = mapper.createErrorEvent(command, errorStr)

    private fun createEvent(command: CommandDTO, eventType: EventType, payload: Any?) = mapper.createEvent(command, eventType, payload)

    private fun doGenerateBrackets(com: CommandDTO): List<EventDTO> =
            executeValidated(com, GenerateBracketsPayload::class.java) { payload, command ->
                val competitors = jooqQueryProvider.competitorsQuery(command.competitionId).and(CategoryDescriptor.CATEGORY_DESCRIPTOR.ID.eq(command.categoryId)).fetch { rec -> jooqMappers.mapCompetitorWithoutCategories(rec) }
                if (!competitors.isNullOrEmpty() && stageDescriptorDao.fetchByCategoryId(command.categoryId).isNullOrEmpty()) {
                    val duration = categoryCrudRepository.findById(command.categoryId).fightDuration!!
                    val stages = payload.stageDescriptors.sortedBy { it.stageOrder }
                    val stageIdMap = stages
                            .map { stage -> stage.id!! to IDGenerator.stageId(command.competitionId, command.categoryId!!) }
                            .toMap()
                    val updatedStages = stages.map { stage ->
                        val outputSize = when (stage.stageType) {
                            StageType.PRELIMINARY -> stage.stageResultDescriptor.outputSize!!
                            else -> 0
                        }
                        val stageId = stageIdMap[stage.id] ?: error("Stage id not found.")
                        val groupDescr = stage.groupDescriptors?.map { it ->
                            it.setId(IDGenerator.groupId(stageId))
                        }?.toTypedArray()
                        val inputDescriptor = stage.inputDescriptor?.setId(stageId)?.setSelectors(stage.inputDescriptor
                                ?.selectors
                                ?.mapIndexed { index, sel ->
                                    sel.setId("$stageId-s-$index")
                                            .setApplyToStageId(stageIdMap[sel.applyToStageId])
                                }?.toTypedArray())
                        val resultDescriptor = stage.stageResultDescriptor
                                .setId(stageId)
                                .setFightResultOptions(stage.stageResultDescriptor?.fightResultOptions?.map {
                                    it
                                            .setId(IDGenerator.hashString("$stageId-${UUID.randomUUID()}"))
                                            .setLoserAdditionalPoints(it.loserAdditionalPoints ?: BigDecimal.ZERO)
                                            .setLoserPoints(it.loserPoints ?: BigDecimal.ZERO)
                                            .setWinnerAdditionalPoints(it.winnerAdditionalPoints ?: BigDecimal.ZERO)
                                            .setWinnerPoints(it.winnerAdditionalPoints ?: BigDecimal.ZERO)
                                }?.toTypedArray())
                        val status = if (stage.stageOrder == 0) StageStatus.WAITING_FOR_APPROVAL else StageStatus.WAITING_FOR_COMPETITORS
                        val stageWithIds = stage
                                .setCategoryId(command.categoryId)
                                .setId(stageId)
                                .setStageStatus(status)
                                .setCompetitionId(command.competitionId)
                                .setGroupDescriptors(groupDescr)
                                .setInputDescriptor(inputDescriptor)
                                .setStageResultDescriptor(resultDescriptor)
                        val size = if (stage.stageOrder == 0) competitors.size else stage.inputDescriptor.numberOfCompetitors!!
                        val comps = if (stage.stageOrder == 0) {
                            competitors
                        } else {
                            emptyList()
                        }
                        val twoFighterFights = fightsGenerateService.generateStageFights(command.competitionId, command.categoryId,
                                stageWithIds,
                                size, duration, comps, outputSize)
                        stageWithIds
                                .setName(stageWithIds.name ?: "Default brackets")
                                .setStageStatus(status)
                                .setInputDescriptor(inputDescriptor)
                                .setStageResultDescriptor(stageWithIds.stageResultDescriptor)
                                .setStageStatus(StageStatus.WAITING_FOR_COMPETITORS)
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
                    listOf(createErrorEvent(command, "Category has no competitors or brackets are already generated."))
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

    private fun updateStageStatus(command: CommandDTO): List<EventDTO> = executeValidated(command, UpdateStageStatusPayload::class.java) { payload, c ->
        jooq.fetchStageById(c.competitionId, payload.stageId).flatMap { stage ->
            if (stage.stageStatus == payload.status) {
                Mono.empty()
            } else {
                when (payload.status) {
                    StageStatus.FINISHED, StageStatus.IN_PROGRESS -> Mono.just(listOf(createEvent(c, EventType.STAGE_STATUS_UPDATED, StageStatusUpdatedPayload(payload.stageId, payload.status))))
                    StageStatus.WAITING_FOR_APPROVAL, StageStatus.APPROVED -> {
                        jooq.fetchFightsByStageId(c.competitionId, payload.stageId).collectList().map { stageFights ->
                            val dirtyStageFights = stageFights.applyConditionalUpdate({ it.status == FightStatus.UNCOMPLETABLE }, { it.setStatus(FightStatus.PENDING) })
                            val markedStageFights = FightsService.markAndProcessUncompletableFights(dirtyStageFights, payload.status) { id ->
                                (dirtyStageFights.firstOrNull { it.id == id }
                                        ?: stageFights.firstOrNull { it.id == id })?.scores?.map { it.toPojo(id) }
                            }
                            listOf(createEvent(c, EventType.STAGE_STATUS_UPDATED, StageStatusUpdatedPayload(payload.stageId, payload.status)),
                                    createEvent(c, EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
                                            FightEditorChangesAppliedPayload()
                                                    .setUpdates(markedStageFights.toTypedArray())))
                        }
                    }
                    StageStatus.WAITING_FOR_COMPETITORS -> {
                        jooq.fetchFightsByStageId(c.competitionId, payload.stageId).collectList().map { stageFights ->
                            val dirtyStageFights = stageFights.applyConditionalUpdate({ it.status == FightStatus.UNCOMPLETABLE }, { it.setStatus(FightStatus.PENDING) })
                            listOf(createEvent(c, EventType.STAGE_STATUS_UPDATED, StageStatusUpdatedPayload(payload.stageId, payload.status)),
                                    createEvent(c, EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
                                            FightEditorChangesAppliedPayload()
                                                    .setUpdates(dirtyStageFights.toTypedArray())))
                        }
                    }
                    else -> Mono.empty()
                }
            }
        }.block(Duration.ofMillis(500)).orEmpty()
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
}