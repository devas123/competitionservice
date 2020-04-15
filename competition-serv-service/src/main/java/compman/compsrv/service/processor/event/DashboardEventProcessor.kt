package compman.compsrv.service.processor.event

import arrow.core.Tuple4
import com.compmanager.compservice.jooq.tables.daos.CompScoreDao
import com.compmanager.compservice.jooq.tables.daos.CompetitorDao
import com.compmanager.compservice.jooq.tables.daos.FightDescriptionDao
import com.compmanager.compservice.jooq.tables.pojos.CompScore
import com.compmanager.compservice.jooq.tables.records.CompScoreRecord
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorsPropagatedToStagePayload
import compman.compsrv.model.events.payload.DashboardFightOrderChangedPayload
import compman.compsrv.model.events.payload.FightCompetitorsAssignedPayload
import compman.compsrv.model.events.payload.StageResultSetPayload
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.JooqRepository
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.getPayloadAs
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min

@Component
class DashboardEventProcessor(private val compScoreCrudRepository: CompScoreDao,
                              private val fightDescriptionDao: FightDescriptionDao,
                              private val jooqRepository: JooqRepository,
                              private val fightsService: FightServiceFactory,
                              private val competitorDao: CompetitorDao,
                              validators: List<PayloadValidator>,
                              mapper: ObjectMapper) : AbstractEventProcessor(mapper, validators) {
    override fun affectedEvents(): Set<EventType> {
        return setOf(EventType.DASHBOARD_FIGHT_ORDER_CHANGED,
                EventType.DASHBOARD_FIGHT_RESULT_SET,
                EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED,
                EventType.DASHBOARD_STAGE_RESULT_SET,
                EventType.COMPETITORS_PROPAGATED_TO_STAGE
        )
    }

    override fun applyEvent(event: EventDTO): List<EventDTO> {
        return when (event.type) {
            EventType.COMPETITORS_PROPAGATED_TO_STAGE -> {
                executeValidated(event, CompetitorsPropagatedToStagePayload::class.java) { p, _ ->
                    val competitorIdToFightId = p.competitorIdToFightId
                    val compScores = competitorIdToFightId.toList()
                            .groupBy { it.second }.mapValues { e ->
                                e.value.mapIndexed { ind, p ->
                                    CompScoreRecord(0, 0, 0, null, p.first, p.second, ind)
                                }
                            }.values.flatten()
                    compScores.forEach { it.store() }
                }
            }
            EventType.DASHBOARD_STAGE_RESULT_SET -> {
                val payload = mapper.getPayloadAs(event, StageResultSetPayload::class.java)
                if (payload != null && !payload.stageId.isNullOrBlank() && !payload.results.isNullOrEmpty()) {
                    jooqRepository.saveCompetitorResults(payload.results.toList())
                    listOf(event)
                } else {
                    throw EventApplyingException("stage ID is not provided.", event)
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
                val payload = mapper.getPayloadAs(event, SetFightResultPayload::class.java)
                if (payload != null && !payload.fightId.isNullOrBlank() && payload.fightResult != null && !payload.scores.isNullOrEmpty()) {
                    jooqRepository.updateFightResult(payload.fightId!!, payload.scores.toList(), payload.fightResult, FightStatus.FINISHED)
                    listOf(event)
                } else {
                    throw EventApplyingException("Not enough information in the payload. $payload", event)
                }
            }
            EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED -> {
                val payload = mapper.getPayloadAs(event, FightCompetitorsAssignedPayload::class.java)
                if (payload != null && !payload.fightId.isNullOrBlank() && !payload.compscores.isNullOrEmpty()) {
                    setCompScores(payload.fightId!!, payload.compscores!!)
                    listOf(event)
                } else {
                    throw EventApplyingException("Not enough information in the payload. $payload", event)
                }

            }
            else -> emptyList()
        }
    }

    private fun setCompScores(fightId: String, compScores: Array<CompScoreDTO>) {
        val existingScores = compScoreCrudRepository.fetchByCompscoreFightDescriptionId(fightId)
        val existingFreeScores = existingScores.filter { !it.compscoreCompetitorId.isNullOrBlank() }
        if (existingFreeScores.size < 2) {
            val scores = compScores.filter { cs -> existingScores.none { it.compscoreCompetitorId == cs.competitorId } }
            val firstFreeSlot = (0..existingScores.size).first { existingFreeScores.none {  cs -> cs.compScoreOrder == it } }
            if (!compScores.isNullOrEmpty()) {
                jooqRepository.saveCompScores(
                        scores.map { compScore ->
                            CompScoreRecord(compScore.score.advantages, compScore.score.penalties, compScore.score.points,
                                    compScore.placeholderId, compScore.competitorId, fightId, firstFreeSlot)
                        })
            }
        } else {
            throw IllegalStateException("Trying to set competitor to fight that is already packed. $existingScores")
        }
    }
}