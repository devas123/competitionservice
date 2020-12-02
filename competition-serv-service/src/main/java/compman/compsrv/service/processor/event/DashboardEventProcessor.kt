package compman.compsrv.service.processor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component
import java.util.*
import kotlin.math.max
import kotlin.math.min

@Component
class DashboardEventProcessor(validators: List<PayloadValidator>,
                              mapper: ObjectMapper) {
/*
    override fun affectedEvents(): Set<EventType> {
        return setOf(EventType.DASHBOARD_FIGHT_ORDER_CHANGED,
                EventType.DASHBOARD_FIGHT_RESULT_SET,
                EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED,
                EventType.DASHBOARD_STAGE_RESULT_SET,
                EventType.COMPETITORS_PROPAGATED_TO_STAGE
        )
    }

    override fun applyEvent(event: EventDTO) {
        when (event.type) {
            EventType.COMPETITORS_PROPAGATED_TO_STAGE -> {
                executeValidated(event, CompetitorsPropagatedToStagePayload::class.java) { payload, _ ->
                    val propagations = payload.propagations
                    val compScores = propagations
                            .groupBy { it.toFightId }
                            .mapValues { entry ->
                                entry.value.mapIndexed { ind, p ->
                                    CompScoreRecord(0, 0, 0, null, null, null, p.competitorId, p.toFightId, ind)
                                }
                            }.values.flatten()
                    jooqRepository.saveCompScores(compScores)
                }
            }
            EventType.DASHBOARD_STAGE_RESULT_SET -> {
                executeValidated(event, StageResultSetPayload::class.java) { payload, _ ->
                    jooqRepository.saveCompetitorResults(payload.results.toList())
                    jooqRepository.updateStageStatus(payload.stageId, StageStatus.FINISHED)
                }
            }
            EventType.DASHBOARD_FIGHT_ORDER_CHANGED -> {
                executeValidated(event, DashboardFightOrderChangedPayload::class.java) { payload, _ ->
                    if (payload.newMatId != payload.currentMatId) {
                        //if mats are different
                        //first reduce numbers on the current mat
                        jooqRepository.batchUpdateStartTimeAndNumberFromTo(payload.fightId, payload.currentMatId, false, payload.fightDuration, payload.currentOrderOnMat)
                        //increase numbers on the new mat after the fight
                        jooqRepository.batchUpdateStartTimeAndNumberFromTo(payload.fightId, payload.newMatId, true, payload.fightDuration, payload.newOrderOnMat)
                        //update fight
                        jooqRepository.updateFightMatAndNumberOnMat(payload.fightId, payload.newMatId, payload.newOrderOnMat)
                    } else {
                        //mats are the same
                        //first reduce numbers on the current mat
                        jooqRepository.batchUpdateStartTimeAndNumberFromTo(payload.fightId, payload.currentMatId, payload.currentOrderOnMat > payload.newOrderOnMat,
                                payload.fightDuration, min(payload.currentOrderOnMat, payload.newOrderOnMat), max(payload.currentOrderOnMat, payload.newOrderOnMat))
                        //update fight
                        jooqRepository.updateFightMatAndNumberOnMat(payload.fightId, payload.newMatId, payload.newOrderOnMat)
                    }
                }
            }
            EventType.DASHBOARD_FIGHT_RESULT_SET -> {
                executeValidated(event, SetFightResultPayload::class.java) { payload, _ ->
                    jooqRepository.updateFightResult(payload.fightId!!, payload.scores.toList(), payload.fightResult, FightStatus.FINISHED)
                }
            }
            EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED -> {
                executeValidated(event, FightCompetitorsAssignedPayload::class.java) { payload, _ ->
                    setCompScores(payload.assignments!!)
                }
            }
            else ->
                throw EventApplyingException("No event handler for event ${event.type}", event)
        }
    }

    private fun setCompScores(assignments: Array<CompetitorAssignmentDescriptor>) {
        val existingScores = compScoreCrudRepository.fetchByCompscoreFightDescriptionId(*assignments.map { it.toFightId }.toTypedArray())
        val newScores = assignments.map { a ->
            val targetScore = existingScores.find { it.parentFightId == a.fromFightId }
                    ?: error("No target score for ${a.fromFightId}")
            CompScoreRecord(
                    targetScore.advantages ?: 0,
                    targetScore.penalties ?: 0,
                    targetScore.points ?: 0,
                    targetScore.placeholderId ?: IDGenerator.uid(),
                    targetScore.parentFightId ?: a.fromFightId,
                    targetScore.parentReferenceType ?: a.referenceType?.name,
                    a.competitorId,
                    targetScore.compscoreFightDescriptionId ?: a.toFightId,
                    targetScore.compScoreOrder!!
            )
        }
        jooqRepository.saveCompScores(newScores)
    }
*/
}