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
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

@Component
class DashboardEventProcessor(private val compScoreCrudRepository: CompScoreDao,
                              private val fightDescriptionDao: FightDescriptionDao,
                              private val jooqRepository: JooqRepository,
                              private val fightsService: FightServiceFactory,
                              private val competitorDao: CompetitorDao,
                              validators: ObjectProvider<List<PayloadValidator>>,
                              mapper: ObjectMapper) : AbstractEventProcessor(mapper, validators.ifAvailable.orEmpty()) {
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
                            .groupBy { it.second }.mapValues { e -> e.value.mapIndexed {ind,  p ->
                        CompScoreRecord(0, 0, 0, null, p.first, p.second, ind)
                            } }.values.flatten()
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
                val payload = mapper.getPayloadAs(event, DashboardFightOrderChangedPayload::class.java)
                if (payload != null && !payload.changedFights.isNullOrEmpty()) {
                    jooqRepository.batchUpdateStartTimeAndMatAndNumberOnMatById(payload.changedFights.map { cf ->
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
        if (existingScores.size < 2) {
            val scores = compScores.filter { cs -> existingScores.none { it.compscoreCompetitorId == cs.competitor.id } }
            if (!compScores.isNullOrEmpty()) {
                compScoreCrudRepository.insert(
                        scores.map { compScore ->
                            CompScore(compScore.score.advantages, compScore.score.penalties, compScore.score.points,
                                    compScore.placeholderId, compScore.competitor.id, fightId, compScore.order)
                        })
            }
        } else {
            throw IllegalStateException("Trying to set competitor to fight that is already packed. $existingScores")
        }
    }
}