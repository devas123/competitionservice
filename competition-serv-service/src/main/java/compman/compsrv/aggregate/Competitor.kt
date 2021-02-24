package compman.compsrv.aggregate

import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.Payload
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import java.util.concurrent.atomic.AtomicLong

data class Competitor(val competitorDTO: CompetitorDTO): AbstractAggregate(AtomicLong(0), AtomicLong(0)) {

    fun process(payload: ChangeCompetitorCategoryPayload, com: CommandDTO, createEvent: (command: CommandDTO, eventType: EventType, payload: Payload?) -> EventDTO): List<EventDTO> {
        val competitorId = payload.fighterId
        val newCategoryId = payload.newCategoryId
        return if (!newCategoryId.isNullOrBlank() && !competitorId.isNullOrBlank()) {
            listOf(createEvent(com, EventType.COMPETITOR_CATEGORY_CHANGED, payload))
        } else {
            throw IllegalArgumentException("New category or such competitor does not exist")
        }
    }
}