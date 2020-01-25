package compman.compsrv.service.processor.command

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.orElse
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.DashboardFightOrderChangePayload
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.brackets.CompetitorResultType
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.FightResultDTO
import compman.compsrv.model.dto.competition.FightStage
import compman.compsrv.model.dto.competition.ScoreDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.DashboardFightOrderChange
import compman.compsrv.model.events.payload.DashboardFightOrderChangedPayload
import compman.compsrv.model.events.payload.FightCompetitorsAssignedPayload
import compman.compsrv.repository.*
import compman.compsrv.service.FightsGenerateService
import compman.compsrv.service.ScheduleService
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.createEvent
import compman.compsrv.util.getPayloadAs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.stream.Collectors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

@Component
class DashboardCommandProcessor(private val scheduleService: ScheduleService,
                                private val clusterSession: ClusterSession,
                                private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                                private val competitorCrudRepository: CompetitorCrudRepository,
                                private val categoryCrudRepository: CategoryStateCrudRepository,
                                private val fightCrudRepository: FightCrudRepository,
                                private val competitionPropertiesCrudRepository: CompetitionPropertiesCrudRepository,
                                private val stageDescriptorCrudRepository: StageDescriptorCrudRepository,
                                private val registrationGroupCrudRepository: RegistrationGroupCrudRepository,
                                private val registrationPeriodCrudRepository: RegistrationPeriodCrudRepository,
                                private val registrationInfoCrudRepository: RegistrationInfoCrudRepository,
                                private val dashboardStateCrudRepository: DashboardStateCrudRepository,
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
                    val fight = fightCrudRepository.getOne(payload.fightId)
                    val periodId = fight.period
                    when (fight.stage) {
                        FightStage.IN_PROGRESS, FightStage.FINISHED -> {
                            listOf(mapper.createErrorEvent(command, "Cannot move fight that is finished or in progress."))
                        }
                        else -> {
                            if (payload.newMatId != payload.currentMatId) {
                                log.info("Moving fight $fight to the new mat: ${payload.newMatId}.")
                                //all the fights after current on the current mat -> number - 1
                                val fightsToMoveOnCurrentMat = fightCrudRepository.findDistinctByMatIdAndCompetitionIdAndNumberOnMatGreaterThanEqualAndStageNotInOrderByNumberOnMat(payload.currentMatId,
                                        command.competitionId, fight.numberOnMat!! + 1, listOf(FightStage.FINISHED, FightStage.IN_PROGRESS))
                                val fightOrderChangesCurrentMat = fightsToMoveOnCurrentMat?.map {
                                    DashboardFightOrderChange().setFightId(it.id).setNewMatId(it.matId).setNewOrderOnMat(it.numberOnMat!! - 1).setNewStartTime(it.startTime!!.minus(Duration.ofMinutes(fight.duration!!.toLong())))
                                }?.collect(Collectors.toList())?.toList() ?: emptyList()

                                //fights on the new mat:
                                //all the fights after current -> number + 1
                                val fightsToMoveOnTheNewMat = fightCrudRepository.findDistinctByMatIdAndCompetitionIdAndNumberOnMatGreaterThanEqualAndStageNotInOrderByNumberOnMat(payload.newMatId,
                                        command.competitionId, newOrderOnMat, listOf(FightStage.FINISHED, FightStage.IN_PROGRESS))

                                val fightOrderChangesNewMat = fightsToMoveOnTheNewMat?.map {
                                    DashboardFightOrderChange().setFightId(it.id).setNewMatId(it.matId).setNewOrderOnMat(it.numberOnMat!! + 1).setNewStartTime(it.startTime!!.plus(Duration.ofMinutes(fight.duration!!.toLong())))
                                }?.collect(Collectors.toList())?.toList() ?: emptyList()
                                val newStartTimeOfTheCurrentFight = Option.fromNullable(fightOrderChangesNewMat).flatMap { Option.fromNullable(it.firstOrNull()) }.map { f -> f.newStartTime!! }
                                        .orElse {
                                            Option.fromNullable(fightCrudRepository.findDistinctByMatIdAndCompetitionIdAndNumberOnMatLessThanAndStageNotInOrderByNumberOnMatDesc(payload.newMatId,
                                                    command.competitionId, newOrderOnMat, listOf(FightStage.FINISHED, FightStage.IN_PROGRESS))).flatMap { Option.fromNullable(it.findFirst().orElse(null)) }
                                                    .map { f -> f.startTime!! }
                                        }
                                        .fold({ fight.startTime!! }, { it })
                                val currentFightOrderChange = DashboardFightOrderChange().setFightId(fight.id).setNewMatId(payload.newMatId).setNewOrderOnMat(newOrderOnMat).setNewStartTime(newStartTimeOfTheCurrentFight)

                                val allChanges = (fightOrderChangesNewMat + fightOrderChangesCurrentMat + currentFightOrderChange).distinctBy { it.fightId }
                                log.info("Full list of changes: $allChanges")
                                listOf(mapper.createEvent(command, EventType.DASHBOARD_FIGHT_ORDER_CHANGED, DashboardFightOrderChangedPayload(periodId, allChanges.toTypedArray())))
                            } else {
                                if (fight.numberOnMat!! != newOrderOnMat) {
                                    val sign = sign((fight.numberOnMat!! - newOrderOnMat).toDouble()).toInt()
                                    val start = min(fight.numberOnMat!!, newOrderOnMat) + (1 - sign) / 2
                                    val end = max(fight.numberOnMat!!, newOrderOnMat) - (1 + sign) / 2
                                    val fightsToMoveOnCurrentMat = fightCrudRepository.findDistinctByMatIdAndCompetitionIdAndNumberOnMatBetweenAndStageNotInOrderByNumberOnMat(payload.newMatId,
                                            command.competitionId, start, end, listOf(FightStage.FINISHED, FightStage.IN_PROGRESS))
                                    val fightOrderChangesCurrentMat = fightsToMoveOnCurrentMat?.map {
                                        val newStartTime = if (sign > 0) {
                                            it.startTime!!.plus(Duration.ofMinutes(fight.duration!!.toLong()))
                                        } else {
                                            it.startTime!!.minus(Duration.ofMinutes(fight.duration!!.toLong()))
                                        }
                                        DashboardFightOrderChange().setFightId(it.id).setNewMatId(payload.newMatId).setNewOrderOnMat(it.numberOnMat!! + sign).setNewStartTime(newStartTime)
                                    }?.collect(Collectors.toList())?.toList() ?: emptyList()

                                    val newStartTimeOfTheCurrentFight = Option.fromNullable(fightOrderChangesCurrentMat.lastOrNull()).map { it.newStartTime }.map {
                                        if (sign > 0) {
                                            it.minus(Duration.ofMinutes(fight.duration!!.toLong()))
                                        } else {
                                            it.plus(Duration.ofMinutes(fight.duration!!.toLong()))
                                        }
                                    }.getOrElse { fight.startTime!! }
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
                val payload = mapper.getPayloadAs(command, SetFightResultPayload::class.java)!!
                fun moveFightersToSiblings(fightIds: List<String?>, winnerId: String, compScores: Array<CompScoreDTO>, isSibling: Boolean = false): List<EventDTO> {
                    fun newCompScores(winner: Boolean) = arrayOf(compScores.first {  (winner && it.competitor.id == winnerId) || (!winner && it.competitor.id != winnerId) }.setId(IDGenerator.compScoreId(winnerId))
                            .setScore(ScoreDTO().setAdvantages(0).setPenalties(0).setPoints(0)))

                    fun loser() = compScores.first {  it.competitor.id != winnerId }.competitor.id
                    val ids = fightIds.mapIndexed { index, id -> id to (index == 0) }.filter { !it.first.isNullOrBlank() }
                    return ids.flatMap { idAndWinFight ->
                        val id = idAndWinFight.first
                        val winner = idAndWinFight.second
                        val idToSet = if (winner) { winnerId } else { loser() }
                        if (!id.isNullOrBlank() && fightCrudRepository.existsById(id)) {
                            val processedFight = fightCrudRepository.getOne(id)
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
                                                    .setId(IDGenerator.compScoreId(it.competitor.id!!))
                                                    .setScore(ScoreDTO()
                                                            .setAdvantages(0)
                                                            .setPenalties(0)
                                                            .setPoints(0))
                                        }.toTypedArray())))
                            }
                            fightResultSetAndWinnerMovedForward +
                                    if (!checkIfFightCanBePacked(id) && (!processedFight.winFight.isNullOrBlank() || !processedFight.loseFight.isNullOrBlank())) {
                                        moveFightersToSiblings(listOf(processedFight.winFight, processedFight.loseFight), idToSet, compScores, true)
                                    } else {
                                        emptyList()
                                    }
                        } else {
                            emptyList()
                        }
                    }
                }

                val fight = fightCrudRepository.getOne(payload.fightId)
                if (!payload.fightResult?.winnerId.isNullOrBlank()) {
                    result +
                            mapper.createEvent(command, EventType.DASHBOARD_FIGHT_RESULT_SET, payload) +
                            moveFightersToSiblings(listOf(fight.winFight, fight.loseFight), payload.fightResult.winnerId, payload.scores)
                } else {
                    result
                }
            }
            else -> emptyList()
        }
    }

    fun checkIfFightCanBePacked(fightId: String) = FightsGenerateService.checkIfFightCanBePacked(fightId) { fightCrudRepository.getOne(it) }
}