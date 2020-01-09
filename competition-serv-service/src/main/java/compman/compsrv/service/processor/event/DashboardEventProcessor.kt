package compman.compsrv.service.processor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.DashboardFightOrderChangedPayload
import compman.compsrv.repository.*
import compman.compsrv.util.getPayloadAs
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DashboardEventProcessor(private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                              private val scheduleCrudRepository: ScheduleCrudRepository,
                              private val competitorCrudRepository: CompetitorCrudRepository,
                              private val bracketsCrudRepository: BracketsCrudRepository,
                              private val categoryDescriptorCrudRepository: CategoryDescriptorCrudRepository,
                              private val fightCrudRepository: FightCrudRepository,
                              private val registrationGroupCrudRepository: RegistrationGroupCrudRepository,
                              private val registrationPeriodCrudRepository: RegistrationPeriodCrudRepository,
                              private val registrationInfoCrudRepository: RegistrationInfoCrudRepository,
                              private val mapper: ObjectMapper) : IEventProcessor {
    override fun affectedEvents(): Set<EventType> {
        return setOf(EventType.DASHBOARD_FIGHT_ORDER_CHANGED)
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
                }
                listOf(event)
            }
            else -> emptyList()
        }
    }
}