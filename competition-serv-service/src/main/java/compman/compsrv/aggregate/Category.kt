package compman.compsrv.aggregate

import arrow.core.Either
import arrow.core.curry
import arrow.core.right
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.RocksDBOperations
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.processor.command.*
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.applyConditionalUpdate
import compman.compsrv.util.copy
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max


class Category(val id: String, private val descriptor: CategoryDescriptorDTO, val fights: Array<FightDescriptionDTO> = emptyArray(), val stages: Array<StageDescriptorDTO> = emptyArray()) : AbstractAggregate(AtomicLong(0), AtomicLong(0)) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Category

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    var numberOfCompetitors: Int = 0
        private set

    fun createFightEditorChangesAppliedEvents(command: CommandDTO, newFights: List<FightDescriptionDTO>, updates: List<FightDescriptionDTO>, removeFightIds: List<String>,
                                              createEvent: CreateEvent): List<EventDTO> {
        val allFights = newFights.map { LabeledFight(it, LabeledFight.NEW) } + updates.map { LabeledFight(it, LabeledFight.UPDATED) } + removeFightIds.map { LabeledFight(Either.left(Unit), LabeledFight.REMOVED, it.right()) }
        return allFights.chunked(50) { chunk ->
            createEvent(command, EventType.FIGHTS_EDITOR_CHANGE_APPLIED, FightEditorChangesAppliedPayload()
                    .setNewFights(chunk.filter { it.label == LabeledFight.NEW }.mapNotNull { it.fight.orNull() }.toTypedArray())
                    .setUpdates(chunk.filter { it.label == LabeledFight.UPDATED }.mapNotNull { it.fight.orNull() }.toTypedArray())
                    .setRemovedFighids(chunk.filter { it.label == LabeledFight.REMOVED }.mapNotNull { it.id.orNull() }.toTypedArray()))
        }
    }

    override fun applyEvent(eventDTO: EventDTO, rocksDBOperations: RocksDBOperations) {
        if (eventDTO.version != version.get()) {
            throw EventApplyingException("Version mismatch: ${eventDTO.version} != ${version.get()}", eventDTO)
        }
    }

    override fun applyEvents(events: List<EventDTO>, rocksDBOperations: RocksDBOperations) {
        events.forEach { e -> applyEvent(e, rocksDBOperations) }
    }

    fun process(payload: UpdateStageStatusPayload, c: CommandDTO, createEvent: CreateEvent): List<EventDTO> {
        val stage = stages.first { it.id == payload.stageId }
        val stageFights = fights.filter { it.stageId == stage.id }
        val version = version.get()
        return when (payload.status) {
            StageStatus.FINISHED, StageStatus.IN_PROGRESS -> listOf(createEvent(c, EventType.STAGE_STATUS_UPDATED, StageStatusUpdatedPayload(payload.stageId, payload.status)))
            StageStatus.WAITING_FOR_APPROVAL, StageStatus.APPROVED -> {
                val dirtyStageFights = stageFights.applyConditionalUpdate({ it.status == FightStatus.UNCOMPLETABLE }, { it.setStatus(FightStatus.PENDING) })
                val markedStageFights = FightsService.markAndProcessUncompletableFights(dirtyStageFights, payload.status) { id ->
                    (dirtyStageFights.firstOrNull { it.id == id }
                            ?: stageFights.firstOrNull { it.id == id })?.scores?.toList()
                }
                listOf(createEvent(c, EventType.STAGE_STATUS_UPDATED, StageStatusUpdatedPayload(payload.stageId, payload.status))) +
                        createFightEditorChangesAppliedEvents(c, emptyList(), markedStageFights, emptyList(), createEvent).map(this::enrichWithVersionAndNumber.curry()(version))
            }
            StageStatus.WAITING_FOR_COMPETITORS -> {
                val dirtyStageFights = stageFights.applyConditionalUpdate({ it.status == FightStatus.UNCOMPLETABLE }, { it.setStatus(FightStatus.PENDING) })
                listOf(createEvent(c, EventType.STAGE_STATUS_UPDATED, StageStatusUpdatedPayload(payload.stageId, payload.status))) +
                        createFightEditorChangesAppliedEvents(c, emptyList(), dirtyStageFights, emptyList(), createEvent).map(this::enrichWithVersionAndNumber.curry()(version))
            }
            else -> throw IllegalArgumentException("Wrong status: ${payload.status}.")
        }
    }

    fun process(payload: DashboardFightOrderChangePayload, c: CommandDTO, createEvent: CreateEvent): List<EventDTO> {
        val newOrderOnMat = max(payload.newOrderOnMat, 0)
        val fight = fights.first { it.id == payload.fightId }
        val periodId = fight.period
        return when (fight.status) {
            FightStatus.IN_PROGRESS, FightStatus.FINISHED -> {
                throw IllegalArgumentException("Cannot move fight that is finished or in progress.")
            }
            else -> {
                listOf(createEvent(c, EventType.DASHBOARD_FIGHT_ORDER_CHANGED, DashboardFightOrderChangedPayload()
                        .setFightId(fight.id)
                        .setNewOrderOnMat(newOrderOnMat)
                        .setPeriodId(periodId)
                        .setFightDuration(fight.duration)
                        .setCurrentMatId(fight.mat.id)
                        .setCurrentOrderOnMat(fight.numberOnMat)
                        .setNewMatId(payload.newMatId)))
            }
        }
    }

    fun process(payload: SetFightResultPayload, c: CommandDTO, fightsGenerateService: FightServiceFactory, createEvent: CreateEvent): List<EventDTO> {
        val stageId = fights.find { it.id == payload.fightId }?.stageId
                ?: error("Did not find stage id for fight ${payload.fightId}")
        val result = emptyList<EventDTO>()
        val finishedFights = mutableSetOf<String>()
        val stageFights = fights.filter { it.stageId == stageId }
        val fight = stageFights.find { f -> f.id == payload.fightId }
                ?: error("No fight with id ${payload.fightId} found")
        val winnerId = payload.fightResult?.winnerId

        fun getIdToProceed(ref: FightReferenceType): String? {
            return when (ref) {
                FightReferenceType.WINNER ->
                    winnerId
                FightReferenceType.LOSER ->
                    payload.scores?.find { s -> s.competitorId != winnerId }?.competitorId
            }
        }

        val fightUpdates = result +
                if (!winnerId.isNullOrBlank()) {
                    val assignments = mutableListOf<EventDTO>()
                    FightReferenceType.values().forEach { ref ->
                        getIdToProceed(ref)?.let {
                            FightsService.moveFighterToSiblings(it, payload.fightId, ref, stageFights) { fromFightId, toFightId, competitorId ->
                                assignments.add(createEvent(c, EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED, FightCompetitorsAssignedPayload()
                                        .setAssignments(arrayOf(CompetitorAssignmentDescriptor().setFromFightId(fromFightId).setToFightId(toFightId).setCompetitorId(competitorId)
                                                .setReferenceType(ref)))))
                            }
                        }
                    }
                    finishedFights.add(payload.fightId)
                    listOf(createEvent(c, EventType.DASHBOARD_FIGHT_RESULT_SET, payload)) + assignments
                } else {
                    emptyList()
                }

        return fightUpdates + if (checkIfAllStageFightsFinished(fight.stageId, finishedFights)) {
            val stage = stages.first { it.id == stageId }
            val fightsWithResult = stageFights.map { fd ->
                if (fd.id == payload.fightId) {
                    fd.copy(fightResult = payload.fightResult)
                } else {
                    fd
                }
            }
            val fightResultOptions = stage.stageResultDescriptor?.fightResultOptions.orEmpty().toList()
            val stageResults = fightsGenerateService.buildStageResults(stage.bracketType, StageStatus.FINISHED, stage.stageType,
                    fightsWithResult, stage.id!!, stage.competitionId, fightResultOptions)
            listOf(createEvent(c, EventType.DASHBOARD_STAGE_RESULT_SET,
                    StageResultSetPayload()
                            .setStageId(stage.id)
                            .setResults(stageResults.toTypedArray())))
        } else {
            emptyList()
        }
    }

    private fun checkIfAllStageFightsFinished(stageId: String?, additionalFinishedFightIds: Set<String>) = stageId?.let { sid ->
        fights.filter { it.stageId == sid }
                .all { it.status == FightStatus.FINISHED || it.status == FightStatus.WALKOVER || it.status == FightStatus.UNCOMPLETABLE || additionalFinishedFightIds.contains(it.id) }
    }
            ?: false


    private fun findPropagatedCompetitors(stage: StageDescriptorDTO, p: PropagateCompetitorsPayload, fightsGenerateService: FightServiceFactory): List<String> {
        return fightsGenerateService.applyStageInputDescriptorToResultsAndFights(stage.bracketType, stage.inputDescriptor, p.previousStageId,
                { id -> stages.first { it.id == id }.stageResultDescriptor.fightResultOptions.orEmpty().toList() },
                { id -> stages.first { it.id == id }.stageResultDescriptor.competitorResults.orEmpty().toList() },
                { id -> fights.filter { it.stageId == id } })
    }


    fun process(p: PropagateCompetitorsPayload, c: CommandDTO, competitors: List<CompetitorDTO>, fightServiceFactory: FightServiceFactory, createEvent: CreateEvent): List<EventDTO> {
        val stage = stages.find { s -> s.id == p.previousStageId }
                ?: throw IllegalStateException("Cannot get stage with id ${p.previousStageId}")

        val propagatedCompetitors = findPropagatedCompetitors(stage, p, fightServiceFactory).toSet()
        val propagatedStageFights = fights.filter { it.stageId == p.propagateToStageId }


        val competitorIdsToFightIds = fightServiceFactory
                .distributeCompetitors(competitors.filter { propagatedCompetitors.contains(it.id) }, propagatedStageFights, stage.bracketType)
                .fold(emptyList<CompetitorAssignmentDescriptor>()) { acc, f ->
                    val newPairs = f.scores?.mapNotNull {
                        it.competitorId?.let { c ->
                            CompetitorAssignmentDescriptor().setCompetitorId(c)
                                    .setToFightId(f.id)
                        }
                    }.orEmpty()
                    acc + newPairs
                }
        return listOf(createEvent(c, EventType.COMPETITORS_PROPAGATED_TO_STAGE, CompetitorsPropagatedToStagePayload()
                .setStageId(p.propagateToStageId)
                .setPropagations(competitorIdsToFightIds)))
    }

    private fun getMinUnusedOrder(scores: Array<out CompScoreDTO>?, index: Int = 0): Int {
        return if (scores.isNullOrEmpty()) {
            0
        } else {
            (0..scores.size + index).filter { i -> scores.none { s -> s.order == i } }[index]
        }
    }


    fun process(payload: FightEditorApplyChangesPayload, c: CommandDTO, createEvent: (command: CommandDTO, type: EventType, payload: Any) -> EventDTO): List<EventDTO> {
        val version = version.get()
        val stage = stages.first { it.id == payload.stageId }
        val bracketsType = stage.bracketType
        val allStageFights = fights.filter { it.stageId == payload.stageId }
        val stageFights = when (bracketsType) {
            BracketType.GROUP -> {
                val fightsByGroupId = allStageFights.groupBy { it.groupId!! }
                val competitorChangesByGroupId = payload.competitorGroupChanges.groupBy { it.groupId }
                val groups = stage.groupDescriptors
                groups.flatMap { gr ->
                    val groupChanges = competitorChangesByGroupId[gr.id].orEmpty()
                    val groupFights = fightsByGroupId[gr.id].orEmpty()
                    groupChanges.sortedBy { CategoryAggregateService.changePriority[it.changeType] }.fold(groupFights) { acc, ch ->
                        when (ch.changeType!!) {
                            GroupChangeType.ADD -> {
                                createUpdatesWithAddedCompetitor(acc, ch, payload.stageId, c.competitionId, c.categoryId)
                            }
                            GroupChangeType.REMOVE -> {
                                createUpdatesWithRemovedCompetitor(acc, ch, gr)
                            }
                        }
                    }
                }.sortedBy { it.numberInRound }.mapIndexed { i, fightDescriptionDTO -> fightDescriptionDTO.setNumberInRound(i) }
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
                    ?: stageFights.firstOrNull { it.id == id })?.scores?.toList()
        }

        return createFightEditorChangesAppliedEvents(c,
                markedStageFights.filter { allStageFights.none { asf -> asf.id == it.id } },
                markedStageFights.filter { allStageFights.any { asf -> asf.id == it.id } },
                allStageFights.filter { asf -> markedStageFights.none { msf -> asf.id == msf.id } }
                        .map { it.id }, createEvent).map(this::enrichWithVersionAndNumber.curry()(version))
    }

    private tailrec fun clearAffectedFights(fights: List<FightDescriptionDTO>, changedIds: Set<String>): List<FightDescriptionDTO> {
        return if (changedIds.isEmpty()) {
            fights
        } else {
            val affectedFights = fights.filter { fg -> fg.scores?.any { s -> changedIds.contains(s.parentFightId) } == true }
            clearAffectedFights(fights.map { f -> f.setScores(f.scores?.applyConditionalUpdate({ changedIds.contains(it.parentFightId) }, { it.setCompetitorId(null) })) }, affectedFights.map { it.id }.toSet())
        }
    }


    fun process(payload: GenerateBracketsPayload, c: CommandDTO, fightsGenerateService: FightServiceFactory, competitors: List<CompetitorDTO>, createEvent: (command: CommandDTO, type: EventType, payload: Any) -> EventDTO): List<EventDTO> {
        val version = version.get()
        val stages = payload.stageDescriptors.sortedBy { it.stageOrder }
        val stageIdMap = stages
                .map { stage ->
                    (stage.id
                            ?: error("Missing stage id")) to IDGenerator.stageId(c.competitionId, c.categoryId!!)
                }
                .toMap()
        val updatedStages = stages.map { stage ->
            val duration = stage.fightDuration ?: error("Missing fight duration.")
            val outputSize = when (stage.stageType) {
                StageType.PRELIMINARY -> stage.stageResultDescriptor.outputSize!!
                else -> 0
            }
            val stageId = stageIdMap[stage.id] ?: error("Generated stage id not found in the map.")
            val groupDescr = stage.groupDescriptors?.map { it ->
                it.setId(IDGenerator.groupId(stageId))
            }?.toTypedArray()
            val inputDescriptor = stage.inputDescriptor?.setId(stageId)?.setSelectors(stage.inputDescriptor
                    ?.selectors
                    ?.mapIndexed { index, sel ->
                        sel.setId("$stageId-s-$index")
                                .setApplyToStageId(stageIdMap[sel.applyToStageId])
                    }?.toTypedArray())
            val enrichedOptions = stage.stageResultDescriptor?.fightResultOptions.orEmpty().toList() + FightResultOptionDTO.WALKOVER
            val resultDescriptor = stage.stageResultDescriptor
                    .setId(stageId)
                    .setFightResultOptions(enrichedOptions.map {
                        it
                                .setId(it.id ?: IDGenerator.hashString("$stageId-${IDGenerator.uid()}"))
                                .setLoserAdditionalPoints(it.loserAdditionalPoints ?: BigDecimal.ZERO)
                                .setLoserPoints(it.loserPoints ?: BigDecimal.ZERO)
                                .setWinnerAdditionalPoints(it.winnerAdditionalPoints ?: BigDecimal.ZERO)
                                .setWinnerPoints(it.winnerAdditionalPoints ?: BigDecimal.ZERO)
                    }.distinctBy { it.id }.toTypedArray())
            val status = if (stage.stageOrder == 0) StageStatus.WAITING_FOR_APPROVAL else StageStatus.WAITING_FOR_COMPETITORS
            val stageWithIds = stage
                    .setCategoryId(c.categoryId)
                    .setId(stageId)
                    .setStageStatus(status)
                    .setCompetitionId(c.competitionId)
                    .setGroupDescriptors(groupDescr)
                    .setInputDescriptor(inputDescriptor)
                    .setStageResultDescriptor(resultDescriptor)
            val size = if (stage.stageOrder == 0) competitors.size else stage.inputDescriptor.numberOfCompetitors!!
            val comps = if (stage.stageOrder == 0) {
                competitors
            } else {
                emptyList()
            }
            val twoFighterFights = fightsGenerateService.generateStageFights(c.competitionId, c.categoryId,
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
                createEvent(c, EventType.FIGHTS_ADDED_TO_STAGE,
                        FightsAddedToStagePayload(it.toTypedArray(), pair.first.id))
            }
        }
        return listOf(createEvent(c, EventType.BRACKETS_GENERATED, BracketsGeneratedPayload(updatedStages.mapNotNull { it.first }.toTypedArray()))).map(this::enrichWithVersionAndNumber.curry()(version)) + fightAddedEvents.map(this::enrichWithVersionAndNumber.curry()(version))
    }
}