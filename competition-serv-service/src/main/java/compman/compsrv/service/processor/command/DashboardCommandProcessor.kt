package compman.compsrv.service.processor.command

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.orElse
import com.compmanager.compservice.jooq.tables.daos.FightDescriptionDao
import com.compmanager.compservice.jooq.tables.daos.FightResultOptionDao
import com.compmanager.compservice.jooq.tables.daos.StageDescriptorDao
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.mapping.toDTO
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.DashboardFightOrderChangePayload
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.dto.competition.ScoreDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.DashboardFightOrderChange
import compman.compsrv.model.events.payload.DashboardFightOrderChangedPayload
import compman.compsrv.model.events.payload.FightCompetitorsAssignedPayload
import compman.compsrv.model.events.payload.StageResultSetPayload
import compman.compsrv.repository.*
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.service.fight.FightsService
import compman.compsrv.util.copy
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.createEvent
import compman.compsrv.util.getPayloadAs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

@Component
class DashboardCommandProcessor(private val fightCrudRepository: FightDescriptionDao,
                                private val jooqQueries: JooqQueries,
                                private val fightsGenerateService: FightServiceFactory,
                                private val pointsAssignmentDescriptorDao: FightResultOptionDao,
                                private val stageDescriptorCrudRepository: StageDescriptorDao,
                                private val mapper: ObjectMapper) : ICommandProcessor {
    override fun affectedCommands(): Set<CommandType> {
        return setOf(CommandType.DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND,
                CommandType.DASHBOARD_SET_FIGHT_RESULT_COMMAND)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DashboardCommandProcessor::class.java)
    }


    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    override fun executeCommand(command: CommandDTO): List<EventDTO> {
        return when (command.type) {
            CommandType.DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND -> {
                val payload = mapper.getPayloadAs(command, DashboardFightOrderChangePayload::class.java)
                if (payload != null && !payload.fightId.isNullOrBlank() && !payload.currentMatId.isNullOrBlank() && !payload.newMatId.isNullOrBlank() && payload.newOrderOnMat != null && payload.currentOrderOnMat != null) {
                    val newOrderOnMat = max(payload.newOrderOnMat, 0)
                    val fight = fightCrudRepository.findById(payload.fightId)!!
                    val periodId = fight.period
                    when (fight.status) {
                        FightStatus.IN_PROGRESS.ordinal, FightStatus.FINISHED.ordinal -> {
                            listOf(mapper.createErrorEvent(command, "Cannot move fight that is finished or in progress."))
                        }
                        else -> {
                            if (payload.newMatId != payload.currentMatId) {
                                log.info("Moving fight $fight to the new mat: ${payload.newMatId}.")
                                //all the fights after current on the current mat -> number - 1
                                val fightsToMoveOnCurrentMat = jooqQueries.findDistinctByMatIdAndCompetitionIdAndNumberOnMatGreaterThanEqualAndStatusNotInOrderByNumberOnMat(payload.currentMatId,
                                        command.competitionId, fight.numberOnMat!! + 1, FightsService.unMovableFightStatuses)
                                        .collectList().block()
                                val fightOrderChangesCurrentMat = fightsToMoveOnCurrentMat?.map {
                                    DashboardFightOrderChange().setFightId(it.id).setNewMatId(it.matId).setNewOrderOnMat(it.numberOnMat!! - 1)
                                            .setNewStartTime(it.startTime?.toInstant()!!.minus(Duration.ofMinutes(fight.duration!!.toLong())))
                                } ?: emptyList()

                                //fights on the new mat:
                                //all the fights after current -> number + 1
                                val fightsToMoveOnTheNewMat = jooqQueries.findDistinctByMatIdAndCompetitionIdAndNumberOnMatGreaterThanEqualAndStatusNotInOrderByNumberOnMat(payload.newMatId,
                                        command.competitionId, newOrderOnMat, FightsService.unMovableFightStatuses).collectList().block()

                                val fightOrderChangesNewMat = fightsToMoveOnTheNewMat?.map {
                                    DashboardFightOrderChange().setFightId(it.id).setNewMatId(it.matId).setNewOrderOnMat(it.numberOnMat!! + 1)
                                            .setNewStartTime(it.startTime?.toInstant()!!.plus(Duration.ofMinutes(fight.duration!!.toLong())))
                                } ?: emptyList()
                                val newStartTimeOfTheCurrentFight = Option.fromNullable(fightOrderChangesNewMat).flatMap { Option.fromNullable(it.firstOrNull()) }.map { f -> f.newStartTime!! }
                                        .orElse {
                                            Option.fromNullable(jooqQueries.findDistinctByMatIdAndCompetitionIdAndNumberOnMatLessThanAndStatusNotInOrderByNumberOnMatDesc(payload.newMatId,
                                                    command.competitionId, newOrderOnMat, FightsService.unMovableFightStatuses).collectList().block()?.toList()).map { it.firstOrNull() }
                                                    .map { f -> f?.startTime?.toInstant()!! }
                                        }
                                        .fold({ fight.startTime?.toInstant()!! }, { it })
                                val currentFightOrderChange = DashboardFightOrderChange().setFightId(fight.id).setNewMatId(payload.newMatId).setNewOrderOnMat(newOrderOnMat).setNewStartTime(newStartTimeOfTheCurrentFight)

                                val allChanges = (fightOrderChangesNewMat + fightOrderChangesCurrentMat + currentFightOrderChange).distinctBy { it.fightId }
                                log.info("Full list of changes: $allChanges")
                                listOf(mapper.createEvent(command, EventType.DASHBOARD_FIGHT_ORDER_CHANGED, DashboardFightOrderChangedPayload(periodId, allChanges.toTypedArray())))
                            } else {
                                if (fight.numberOnMat!! != newOrderOnMat) {
                                    val sign = sign((fight.numberOnMat!! - newOrderOnMat).toDouble()).toInt()
                                    val start = min(fight.numberOnMat!!, newOrderOnMat) + (1 - sign) / 2
                                    val end = max(fight.numberOnMat!!, newOrderOnMat) - (1 + sign) / 2
                                    val fightsToMoveOnCurrentMat = jooqQueries.findDistinctByMatIdAndCompetitionIdAndNumberOnMatBetweenAndStatusNotInOrderByNumberOnMat(payload.newMatId,
                                            command.competitionId, start, end, FightsService.unMovableFightStatuses).collectList().block()
                                    val fightOrderChangesCurrentMat = fightsToMoveOnCurrentMat?.map {
                                        val newStartTime = if (sign > 0) {
                                            it.startTime?.toInstant()!!.plus(Duration.ofMinutes(fight.duration!!.toLong()))
                                        } else {
                                            it.startTime?.toInstant()!!.minus(Duration.ofMinutes(fight.duration!!.toLong()))
                                        }
                                        DashboardFightOrderChange().setFightId(it.id).setNewMatId(payload.newMatId).setNewOrderOnMat(it.numberOnMat!! + sign).setNewStartTime(newStartTime)
                                    } ?: emptyList()

                                    val newStartTimeOfTheCurrentFight = Option.fromNullable(fightOrderChangesCurrentMat.lastOrNull()).map { it.newStartTime }.map {
                                        if (sign > 0) {
                                            it.minus(Duration.ofMinutes(fight.duration!!.toLong()))
                                        } else {
                                            it.plus(Duration.ofMinutes(fight.duration!!.toLong()))
                                        }
                                    }.getOrElse { fight.startTime?.toInstant()!! }
                                    val currentFightOrderChange = DashboardFightOrderChange().setFightId(fight.id).setNewMatId(payload.newMatId).setNewOrderOnMat(newOrderOnMat).setNewStartTime(newStartTimeOfTheCurrentFight)
                                    val allChanges = (fightOrderChangesCurrentMat + currentFightOrderChange).distinctBy { it.fightId }
                                    listOf(mapper.createEvent(command, EventType.DASHBOARD_FIGHT_ORDER_CHANGED, DashboardFightOrderChangedPayload(periodId, allChanges.toTypedArray())))
                                } else {
                                    listOf(mapper.createErrorEvent(command, "The new position of the fight is equal to the current."))
                                }
                            }
                        }
                    }
                } else {
                    emptyList()
                }
            }
            CommandType.DASHBOARD_SET_FIGHT_RESULT_COMMAND -> {
                val result = emptyList<EventDTO>()
                val updatedFightIds = mutableSetOf<String>()
                val payload = mapper.getPayloadAs(command, SetFightResultPayload::class.java)!!
                fun moveFightersToSiblings(fightIds: List<String?>, winnerId: String, compScores: Array<CompScoreDTO>, isSibling: Boolean = false): List<EventDTO> {
                    fun newCompScores(winner: Boolean) = arrayOf(compScores.first { (winner && it.competitor.id == winnerId) || (!winner && it.competitor.id != winnerId) }
                            .setScore(ScoreDTO().setAdvantages(0).setPenalties(0).setPoints(0)))

                    fun loser() = compScores.first { it.competitor.id != winnerId }.competitor.id
                    val ids = fightIds.mapIndexed { index, id -> id to (index == 0) }.filter { !it.first.isNullOrBlank() }
                    return ids.flatMap { idAndWinFight ->
                        val id = idAndWinFight.first
                        val winner = idAndWinFight.second
                        val idToSet = if (winner) {
                            winnerId
                        } else {
                            loser()
                        }
                        if (!id.isNullOrBlank() && fightCrudRepository.existsById(id)) {
                            val processedFight = fightCrudRepository.findById(id)!!
                            val fightResultSetAndWinnerMovedForward = if (isSibling) {
                                listOf(mapper.createEvent(command, EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED, FightCompetitorsAssignedPayload()
                                        .setFightId(id)
                                        .setCompscores(newCompScores(winner))))
                            } else {
                                listOf(mapper.createEvent(command, EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED, FightCompetitorsAssignedPayload().setFightId(id)
                                        .setCompscores(compScores.filter {
                                            if (winner) {
                                                it.competitor.id == winnerId
                                            } else {
                                                it.competitor.id != winnerId
                                            }
                                        }.map {
                                            it
                                                    .setScore(ScoreDTO()
                                                            .setAdvantages(0)
                                                            .setPenalties(0)
                                                            .setPoints(0))
                                        }.toTypedArray())))
                            }
                            fightResultSetAndWinnerMovedForward +
                                    if (!checkIfFightCanBePacked(command.competitionId, id) && (!processedFight.winFight.isNullOrBlank() || !processedFight.loseFight.isNullOrBlank())) {
                                        moveFightersToSiblings(listOf(processedFight.winFight, processedFight.loseFight), idToSet, compScores, true)
                                    } else {
                                        emptyList()
                                    }
                        } else {
                            emptyList()
                        }
                    }
                }


                val fight = jooqQueries.findFightByCompetitionIdAndId(command.competitionId, payload.fightId).block()!!

                val fightUpdates = result +
                        if (!payload.fightResult?.winnerId.isNullOrBlank()) {
                            updatedFightIds.add(payload.fightId)
                            listOf(mapper.createEvent(command, EventType.DASHBOARD_FIGHT_RESULT_SET, payload)) +
                                    moveFightersToSiblings(listOf(fight.winFight, fight.loseFight), payload.fightResult.winnerId, payload.scores)
                        } else {
                            emptyList()
                        }

                fightUpdates + if (checkIfAllStageFightsFinished(command.competitionId, fight.stageId, updatedFightIds)) {
                    val stage = stageDescriptorCrudRepository.findById(fight.stageId!!)
                    val fightsWithResult = jooqQueries.fetchFightsByStageId(command.competitionId, stage.id!!).collectList().block()?.map { fd ->
                        if (fd.id == payload.fightId) {
                            fd.copy(fightResult = payload.fightResult)
                        } else {
                            fd
                        }
                    }
                    val fightResultOptions = pointsAssignmentDescriptorDao.fetchByStageId(fight.stageId)?.map { it.toDTO() } ?: emptyList()
                    val stageResults = fightsGenerateService.buildStageResults(BracketType.values()[stage.bracketType], StageStatus.FINISHED,
                            fightsWithResult ?: emptyList(), stage.id!!, stage.competitionId, fightResultOptions)
                    listOf(mapper.createEvent(command, EventType.DASHBOARD_STAGE_RESULT_SET,
                            StageResultSetPayload()
                                    .setStageId(stage.id)
                                    .setResults(stageResults.toTypedArray())))
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    fun checkIfAllStageFightsFinished(competitionId: String, stageId: String?, additionalFinishedFightIds: Set<String>) = stageId?.let { jooqQueries.fetchFightsByStageId(competitionId, stageId)
            .all { it.status == FightStatus.FINISHED || it.status == FightStatus.WALKOVER || it.status == FightStatus.UNCOMPLETABLE || additionalFinishedFightIds.contains(it.id) }.block() }
            ?: false

    fun checkIfFightCanBePacked(fightId: String, competitionId: String) = FightsService.checkIfFightCanBePacked(fightId) { jooqQueries.findFightByCompetitionIdAndId(competitionId, it).block()!! }
}