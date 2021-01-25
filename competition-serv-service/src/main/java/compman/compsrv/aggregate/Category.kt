package compman.compsrv.aggregate

import arrow.core.Either
import arrow.core.right
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.CategoryNotFoundException
import compman.compsrv.service.processor.command.CreateEvent
import compman.compsrv.service.processor.command.LabeledFight
import compman.compsrv.util.applyConditionalUpdate
import compman.compsrv.util.applyConditionalUpdateArray
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min


data class Category(
    val id: String,
    private val descriptor: CategoryDescriptorDTO,
    val fights: Array<FightDescriptionDTO> = emptyArray(),
    val stages: Array<StageDescriptorDTO> = emptyArray(),
    val numberOfCompetitors: Int = 0
) : AbstractAggregate(AtomicLong(0), AtomicLong(0)) {
    companion object {
        fun createFightEditorChangesAppliedEvents(
            command: CommandDTO,
            newFights: List<FightDescriptionDTO>,
            updates: List<FightDescriptionDTO>,
            removeFightIds: List<String>,
            createEvent: CreateEvent
        ): List<EventDTO> {
            val allFights = newFights.map { LabeledFight(it, LabeledFight.NEW) } + updates.map {
                LabeledFight(
                    it,
                    LabeledFight.UPDATED
                )
            } + removeFightIds.map { LabeledFight(Either.left(Unit), LabeledFight.REMOVED, it.right()) }
            return allFights.chunked(50) { chunk ->
                createEvent(command, EventType.FIGHTS_EDITOR_CHANGE_APPLIED, FightEditorChangesAppliedPayload()
                    .setNewFights(chunk.filter { it.label == LabeledFight.NEW }.mapNotNull { it.fight.orNull() }
                        .toTypedArray())
                    .setUpdates(chunk.filter { it.label == LabeledFight.UPDATED }.mapNotNull { it.fight.orNull() }
                        .toTypedArray())
                    .setRemovedFighids(chunk.filter { it.label == LabeledFight.REMOVED }.mapNotNull { it.id.orNull() }
                        .toTypedArray())
                )
            }
        }
    }
    private val fightsMap = fights.map { it.id to it }.toMap()
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

    fun stageStatusUpdated(payload: StageStatusUpdatedPayload): Category {
        stages.first { it.id == payload.stageId }.stageStatus = payload.status
        return this
    }

    fun applyFightEditorChanges(payload: FightEditorChangesAppliedPayload): Category {
        val removals = payload.removedFighids.orEmpty().toSet()
        val updates = payload.updates.orEmpty().map { it.id to it }.toMap()
        val newFights = payload.newFights.orEmpty()
        val fights = this.fights.filter { !removals.contains(it.id) } + newFights.toList()
        fights.applyConditionalUpdate({ updates.containsKey(it.id) }, { updates.getValue(it.id) })
        return this.copy(fights = fights.toTypedArray())
    }

    fun dashboardFightOrderChanged(payload: DashboardFightOrderChangedPayload): Category {
        if (payload.newMatId != payload.currentMatId) {
            //if mats are different
            for (f in fights) {
                if (f.id != payload.fightId && f.mat.id == payload.currentMatId && f.numberOnMat != null && f.numberOnMat >= payload.currentOrderOnMat) {
                    //first reduce numbers on the current mat
                    f.numberOnMat = f.numberOnMat - 1
                    f.startTime = f.startTime.minus(payload.fightDuration.toLong(), ChronoUnit.MINUTES)
                } else if (f.id != payload.fightId && f.mat.id == payload.newMatId && f.numberOnMat != null && f.numberOnMat >= payload.newOrderOnMat) {
                    f.numberOnMat = f.numberOnMat + 1
                    f.startTime = f.startTime.plus(payload.fightDuration.toLong(), ChronoUnit.MINUTES)
                } else if (f.id == payload.fightId) {
                    f.mat = f.mat.setId(payload.newMatId)
                    f.numberOnMat = payload.newOrderOnMat
                }
            }
        } else {
            //mats are the same
            for (f in fights) {
                if (f.id != payload.fightId && f.mat.id == payload.currentMatId && f.numberOnMat != null
                    && f.numberOnMat >= min(payload.currentOrderOnMat, payload.newOrderOnMat) &&
                    f.numberOnMat <= max(payload.currentOrderOnMat, payload.newOrderOnMat)
                ) {
                    //first reduce numbers on the current mat
                    if (payload.currentOrderOnMat > payload.newOrderOnMat) {
                        f.numberOnMat = f.numberOnMat + 1
                        f.startTime = f.startTime.plus(payload.fightDuration.toLong(), ChronoUnit.MINUTES)
                    } else {
                        //update fight
                        f.numberOnMat = f.numberOnMat - 1
                        f.startTime = f.startTime.minus(payload.fightDuration.toLong(), ChronoUnit.MINUTES)
                    }
                } else if (f.id == payload.fightId) {
                    f.mat = f.mat.setId(payload.newMatId)
                    f.numberOnMat = payload.newOrderOnMat
                }
            }
        }
        return this
    }

    fun fightCompetitorsAssigned(payload: FightCompetitorsAssignedPayload): Category {
        val assignments = payload.assignments
        for (assignment in assignments) {
            val fromFight = fightsMap[assignment.fromFightId] ?: error("No fight with id ${assignment.fromFightId}")
            val toFight = fightsMap[assignment.toFightId] ?: error("No fight with id ${assignment.toFightId}")
            toFight.scores?.find { it.parentFightId == fromFight.id }?.let {
                it.competitorId = assignment.competitorId
                it.parentReferenceType = it.parentReferenceType ?: assignment.referenceType
            } ?: error("No target score for ${assignment.fromFightId} in fight ${assignment.toFightId}")
        }
        return this
    }

    fun fightResultSet(payload: SetFightResultPayload): Category {
        fightsMap[payload.fightId]?.let { f ->
            f.scores = payload.scores
            f.status = FightStatus.FINISHED
            f.fightResult = payload.fightResult
        }
        return this
    }

    fun stageResultSet(payload: StageResultSetPayload): Category {
        stages.find { it.id == payload.stageId }?.let {
            it.stageStatus = StageStatus.FINISHED
            it.stageResultDescriptor.competitorResults = payload.results
        }
        return this
    }

    fun competitorsPropagatedToStage(payload: CompetitorsPropagatedToStagePayload): Category {
        val propagations = payload.propagations
        propagations
            .groupBy { it.toFightId }
            .entries.forEach { entry ->
                val compScores = entry.value.mapIndexed { ind, p ->
                    CompScoreDTO().setCompetitorId(p.competitorId)
                        .setParentFightId(p.fromFightId)
                        .setOrder(ind)
                        .setParentReferenceType(FightReferenceType.PROPAGATED)
                        .setScore(ScoreDTO().setAdvantages(0).setPoints(0).setPenalties(0))
                }
                fightsMap[entry.key]?.let { f ->
                    f.scores = compScores.toTypedArray() + f.scores.orEmpty()
                }
            }
        return this
    }

    fun bracketsGenerated(payload: BracketsGeneratedPayload): Category {
        val stages = payload.stages
        if (stages != null) {
            return this.copy(stages = stages)
        } else {
            throw CategoryNotFoundException("Fights are null or empty or category ID is empty.")
        }
    }

    fun fightsAddedToStage(payload: FightsAddedToStagePayload): Category {
        val fm = payload.fights.map { it.id to it }.toMap()
        return this.copy(fights = fights.applyConditionalUpdateArray({it.stageId == payload.stageId && fm.containsKey(it.id)}, { fm.getValue(it.id) }) + payload.fights.filter { !fightsMap.containsKey(it.id) })
    }
}