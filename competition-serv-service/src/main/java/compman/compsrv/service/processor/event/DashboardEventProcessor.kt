package compman.compsrv.service.processor.event

import arrow.core.Tuple4
import com.compmanager.compservice.jooq.tables.daos.CompScoreDao
import com.compmanager.compservice.jooq.tables.pojos.CompScore
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.DashboardFightOrderChangedPayload
import compman.compsrv.model.events.payload.FightCompetitorsAssignedPayload
import compman.compsrv.model.events.payload.StageResultSetPayload
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.JooqQueries
import compman.compsrv.util.getPayloadAs
import org.springframework.stereotype.Component

@Component
class DashboardEventProcessor(private val compScoreCrudRepository: CompScoreDao,
                              private val jooqQueries: JooqQueries,
                              private val mapper: ObjectMapper) : IEventProcessor {
    override fun affectedEvents(): Set<EventType> {
        return setOf(EventType.DASHBOARD_FIGHT_ORDER_CHANGED,
                EventType.DASHBOARD_FIGHT_RESULT_SET,
                EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED,
                EventType.DASHBOARD_STAGE_RESULT_SET
        )
    }

    override fun applyEvent(event: EventDTO): List<EventDTO> {
        return when (event.type) {
            EventType.DASHBOARD_STAGE_RESULT_SET -> {
                val payload = mapper.getPayloadAs(event, StageResultSetPayload::class.java)
                if (payload != null && !payload.stageId.isNullOrBlank() && !payload.results.isNullOrEmpty()) {
                    jooqQueries.saveCompetitorResults(payload.results.toList())
                    listOf(event)
                } else {
                    throw EventApplyingException("stage ID is not provided.", event)
                }
            }
            EventType.DASHBOARD_FIGHT_ORDER_CHANGED -> {
                val payload = mapper.getPayloadAs(event, DashboardFightOrderChangedPayload::class.java)
                if (payload != null && !payload.changedFights.isNullOrEmpty()) {
                    jooqQueries.batchUpdateStartTimeAndMatAndNumberOnMatById(payload.changedFights.map { cf ->
                        Tuple4(cf.fightId, cf.newStartTime, cf.newMatId, cf.newOrderOnMat)
                    })
                    listOf(event)
                } else {
                    throw EventApplyingException("Change fights are empty. $payload", event)
                }
            }
            EventType.DASHBOARD_FIGHT_RESULT_SET -> {
                val payload = mapper.getPayloadAs(event, SetFightResultPayload::class.java)
                if (payload != null && !payload.fightId.isNullOrBlank() && payload.fightResult != null && !payload.scores.isNullOrEmpty()) {
                    jooqQueries.updateFightResult(payload.fightId!!, payload.scores.toList(), payload.fightResult, FightStatus.FINISHED)
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
        if (existingScores.size < 2) {
            val scores = compScores.filter { cs -> existingScores.none { it.compscoreCompetitorId == cs.competitor.id } }
            if (!compScores.isNullOrEmpty()) {
                compScoreCrudRepository.insert(
                        scores.map { compScore ->
                            CompScore(compScore.score.advantages, compScore.score.penalties, compScore.score.points, compScore.competitor.id, fightId, compScore.order)
                        })
            }
        } else {
            throw IllegalStateException("Trying to set competitor to fight that is already packed. $existingScores")
        }
    }
}