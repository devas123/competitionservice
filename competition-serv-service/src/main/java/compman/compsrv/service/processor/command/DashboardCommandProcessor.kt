package compman.compsrv.service.processor.command

import com.compmanager.compservice.jooq.tables.daos.*
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.mapping.toDTO
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.DashboardFightOrderChangePayload
import compman.compsrv.model.commands.payload.PropagateCompetitorsPayload
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.brackets.StageType
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.repository.JooqRepository
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.service.fight.FightsService
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.copy
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.createEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import kotlin.math.max

@Component
class DashboardCommandProcessor(private val fightCrudRepository: FightDescriptionDao,
                                private val jooqRepository: JooqRepository,
                                private val fightsGenerateService: FightServiceFactory,
                                private val fightResultOptionDao: FightResultOptionDao,
                                private val competitorStageResultDao: CompetitorStageResultDao,
                                private val competitorDao: CompetitorDao,
                                private val compScoreDao: CompScoreDao,
                                private val stageDescriptorCrudRepository: StageDescriptorDao,
                                validators: List<PayloadValidator>,
                                mapper: ObjectMapper) : AbstractCommandProcessor(mapper, validators) {
    override fun affectedCommands(): Set<CommandType> {
        return setOf(CommandType.DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND,
                CommandType.DASHBOARD_SET_FIGHT_RESULT_COMMAND,
                CommandType.PROPAGATE_COMPETITORS_COMMAND)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DashboardCommandProcessor::class.java)
    }


    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    override fun executeCommand(command: CommandDTO): List<EventDTO> {
        return when (command.type) {
            CommandType.PROPAGATE_COMPETITORS_COMMAND -> propagateCompetitors(command)
            CommandType.DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND -> changeFightOrder(command)
            CommandType.DASHBOARD_SET_FIGHT_RESULT_COMMAND -> setFightResult(command)
            else -> emptyList()
        }
    }

    private fun propagateCompetitors(command: CommandDTO): List<EventDTO> {
        return executeValidated(command, PropagateCompetitorsPayload::class.java) { p, com ->
            val stage = jooqRepository.fetchStageById(com.competitionId, p.previousStageId).block(Duration.ofMillis(300))
                    ?: throw IllegalStateException("Cannot get stage with id ${p.previousStageId}")

            val propagatedCompetitorIds = fightsGenerateService.applyStageInputDescriptorToResultsAndFights(stage.bracketType, stage.inputDescriptor, p.previousStageId,
                    { id -> fightResultOptionDao.fetchByStageId(id).map { it.toDTO() } },
                    { id -> competitorStageResultDao.fetchByStageId(id).map { it.toDTO() } },
                    { id ->
                        jooqRepository.fetchFightsByStageId(com.competitionId, id).collectList().block(Duration.ofMillis(300))
                                .orEmpty()
                    })

            val propagatedCompetitors = competitorDao.fetchById(*propagatedCompetitorIds.toTypedArray()).map { it.toDTO(arrayOf(command.categoryId)) }
            val propagatedStageFights = jooqRepository.fetchFightsByStageId(com.competitionId, p.propagateToStageId).collectList().block(Duration.ofMillis(300))
                    ?: throw IllegalStateException("No fights found for stage ${p.propagateToStageId}")

            val competitorIdsToFightIds = fightsGenerateService
                    .distributeCompetitors(propagatedCompetitors, propagatedStageFights, stage.bracketType)
                    .fold(emptyList<CompetitorAssignmentDescriptor>()) { acc, f ->
                        val newPairs = f.scores?.mapNotNull {
                            it.competitorId?.let { c ->
                                CompetitorAssignmentDescriptor().setCompetitorId(c)
                                        .setToFightId(f.id)
                            }
                        }.orEmpty()
                        acc + newPairs
                    }
            listOf(mapper.createEvent(com, EventType.COMPETITORS_PROPAGATED_TO_STAGE, CompetitorsPropagatedToStagePayload()
                    .setStageId(p.propagateToStageId)
                    .setPropagations(competitorIdsToFightIds)))
        }
    }


    private fun setFightResult(com: CommandDTO): List<EventDTO> {
        return executeValidated(com, SetFightResultPayload::class.java) { payload, command ->
            val result = emptyList<EventDTO>()
            val finishedFights = mutableSetOf<String>()
            val stageId = fightCrudRepository.findById(payload.fightId)?.stageId ?: error("Did not find stage id for fight ${payload.fightId}")

            val stageFights = jooqRepository.fetchFightsByStageId(command.competitionId, stageId).collectList().block()!!
            val fight = stageFights.find { f -> f.id == payload.fightId } ?: error("No fight with id ${payload.fightId} found")
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
                                    assignments.add(mapper.createEvent(command, EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED, FightCompetitorsAssignedPayload()
                                            .setAssignments(arrayOf(CompetitorAssignmentDescriptor().setFromFightId(fromFightId).setToFightId(toFightId).setCompetitorId(competitorId)
                                                    .setReferenceType(ref)))))
                                }
                            }
                        }
                        finishedFights.add(payload.fightId)
                        listOf(mapper.createEvent(command, EventType.DASHBOARD_FIGHT_RESULT_SET, payload)) + assignments
                    } else {
                        emptyList()
                    }

            fightUpdates + if (checkIfAllStageFightsFinished(command.competitionId, fight.stageId, finishedFights)) {
                val stage = stageDescriptorCrudRepository.findById(fight.stageId!!)
                val fightsWithResult = jooqRepository.fetchFightsByStageId(command.competitionId, stage.id!!).map { fd ->
                    if (fd.id == payload.fightId) {
                        fd.copy(fightResult = payload.fightResult)
                    } else {
                        fd
                    }
                }.collectList().block().orEmpty()
                val fightResultOptions = fightResultOptionDao.fetchByStageId(fight.stageId)?.map { it.toDTO() }
                        .orEmpty()
                val stageResults = fightsGenerateService.buildStageResults(BracketType.valueOf(stage.bracketType), StageStatus.FINISHED, StageType.valueOf(stage.stageType),
                        fightsWithResult, stage.id!!, stage.competitionId, fightResultOptions)
                listOf(mapper.createEvent(command, EventType.DASHBOARD_STAGE_RESULT_SET,
                        StageResultSetPayload()
                                .setStageId(stage.id)
                                .setResults(stageResults.toTypedArray())))
            } else {
                emptyList()
            }
        }
    }

    private fun changeFightOrder(command: CommandDTO): List<EventDTO> {
        return executeValidated(command, DashboardFightOrderChangePayload::class.java) { payload, _ ->
            val newOrderOnMat = max(payload.newOrderOnMat, 0)
            val fight = fightCrudRepository.findById(payload.fightId)!!
            val periodId = fight.period
            when (fight.status) {
                FightStatus.IN_PROGRESS.name, FightStatus.FINISHED.name -> {
                    listOf(mapper.createErrorEvent(command, "Cannot move fight that is finished or in progress."))
                }
                else -> {
                    listOf(mapper.createEvent(command, EventType.DASHBOARD_FIGHT_ORDER_CHANGED, DashboardFightOrderChangedPayload()
                            .setFightId(fight.id)
                            .setNewOrderOnMat(newOrderOnMat)
                            .setPeriodId(periodId)
                            .setFightDuration(fight.duration)
                            .setCurrentMatId(fight.matId)
                            .setCurrentOrderOnMat(fight.numberOnMat)
                            .setNewMatId(payload.newMatId)))
                }
            }
        }
    }

    fun checkIfAllStageFightsFinished(competitionId: String, stageId: String?, additionalFinishedFightIds: Set<String>) = stageId?.let {
        jooqRepository.fetchFightsByStageId(competitionId, stageId)
                .all { it.status == FightStatus.FINISHED || it.status == FightStatus.WALKOVER || it.status == FightStatus.UNCOMPLETABLE || additionalFinishedFightIds.contains(it.id) }.block()
    }
            ?: false
}