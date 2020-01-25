package compman.compsrv.service.processor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.FightStage
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.DashboardFightOrderChangedPayload
import compman.compsrv.model.events.payload.FightCompetitorsAssignedPayload
import compman.compsrv.repository.*
import compman.compsrv.util.createErrorEvent
import compman.compsrv.util.getPayloadAs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DashboardEventProcessor(private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                              private val scheduleCrudRepository: ScheduleCrudRepository,
                              private val competitorCrudRepository: CompetitorCrudRepository,
                              private val stageDescriptorCrudRepository: StageDescriptorCrudRepository,
                              private val compScoreCrudRepository: CompScoreCrudRepository,
                              private val categoryDescriptorCrudRepository: CategoryDescriptorCrudRepository,
                              private val fightCrudRepository: FightCrudRepository,
                              private val registrationGroupCrudRepository: RegistrationGroupCrudRepository,
                              private val registrationPeriodCrudRepository: RegistrationPeriodCrudRepository,
                              private val registrationInfoCrudRepository: RegistrationInfoCrudRepository,
                              private val mapper: ObjectMapper) : IEventProcessor {
    override fun affectedEvents(): Set<EventType> {
        return setOf(EventType.DASHBOARD_FIGHT_ORDER_CHANGED,
                EventType.DASHBOARD_FIGHT_RESULT_SET,
                EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(DashboardEventProcessor::class.java)
    }

    override fun applyEvent(event: EventDTO): List<EventDTO> {
        return when (event.type) {
            EventType.DASHBOARD_FIGHT_ORDER_CHANGED -> {
                val payload = mapper.getPayloadAs(event, DashboardFightOrderChangedPayload::class.java)
                if (payload != null && !payload.changedFights.isNullOrEmpty()) {
                    payload.changedFights.forEach { cf ->
                        fightCrudRepository.updateStartTimeAndMatAndNumberOnMatById(cf.fightId, cf.newStartTime, cf.newMatId, cf.newOrderOnMat)
                    }
                    listOf(event)
                } else {
                    log.error("Change fights are empty.")
                    listOf(mapper.createErrorEvent(event, "Change fights are empty. $payload"))
                }
            }
            EventType.DASHBOARD_FIGHT_RESULT_SET -> {
                val payload = mapper.getPayloadAs(event, SetFightResultPayload::class.java)
                if (payload != null && !payload.fightId.isNullOrBlank() && payload.fightResult != null && !payload.scores.isNullOrEmpty()) {
                    payload.scores?.forEach { compScore -> compScoreCrudRepository.updateCompScore(compScore.id, compScore.score.points, compScore.score.advantages, compScore.score.penalties) }
                    val fight = fightCrudRepository.getOne(payload.fightId!!)
                    fight.fightResult = payload.fightResult.toEntity()
                    fight.stage = FightStage.FINISHED
                    listOf(event)
                } else {
                    log.error("Not enough information in the payload. $payload")
                    listOf(mapper.createErrorEvent(event, "Not enough information in the payload. $payload"))
                }
            }
            EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED -> {
                val payload = mapper.getPayloadAs(event, FightCompetitorsAssignedPayload::class.java)
                if (payload != null && !payload.fightId.isNullOrBlank() && !payload.compscores.isNullOrEmpty()) {
                    setCompScores(payload.fightId!!, payload.compscores!!)
                    listOf(event)
                } else {
                    log.error("Not enough information in the payload. $payload")
                    listOf(mapper.createErrorEvent(event, "Not enough information in the payload. $payload"))
                }

            }
            else -> emptyList()
        }
    }

    private fun setCompScores(fightId: String, compScores: Array<CompScoreDTO>) {
        val fight = fightCrudRepository.getOne(fightId)
        if (fight.scores == null) {
            fight.scores = mutableListOf()
        }
        fun saveCompScore(compScore: CompScoreDTO, index: Int, fightId: String) {
            compScoreCrudRepository.insertCompScore(compScore.id, compScore.score.advantages, compScore.score.penalties, compScore.score.points, compScore.competitor.id, fightId, index)
        }

        val existingScores = fight.scores!!
        if (existingScores.size < 2) {
            val scores = compScores.filter { cs -> existingScores.none { it.competitor.id == cs.competitor.id } }
            if (!compScores.isNullOrEmpty()) {
                when {
                    fight.scores!!.isEmpty() -> {
                        scores.forEachIndexed { index, compScore ->
                            saveCompScore(compScore, index, fightId)
                        }
                    }
                    fight.scores!![0] == null -> {
                        saveCompScore(scores.first(), 0, fightId)
                    }
                    else -> {
                        saveCompScore(scores.first(), 1, fightId)
                    }
                }
            }
            fightCrudRepository.save(fight)
        } else {
            throw IllegalStateException("Trying to set competitor to fight that is already packed. $fight")
        }
    }
}