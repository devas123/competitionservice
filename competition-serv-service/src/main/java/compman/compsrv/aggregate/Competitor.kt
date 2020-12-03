package compman.compsrv.aggregate

import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.commands.payload.UpdateCompetitorPayload
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.model.events.payload.CompetitorUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.util.IDGenerator
import java.util.concurrent.atomic.AtomicLong

class Competitor(val competitorDTO: CompetitorDTO): AbstractAggregate(AtomicLong(0), AtomicLong(0)) {
    override fun applyEvent(eventDTO: EventDTO, rocksDBOperations: DBOperations) {
    }

    override fun applyEvents(events: List<EventDTO>, rocksDBOperations: DBOperations) {
    }

    fun process(payload: CompetitorDTO, command: CommandDTO, createEvent: (command: CommandDTO, eventType: EventType, payload: Any?) -> EventDTO): List<EventDTO> {
        val competitorId = IDGenerator.hashString("${command.competitionId}/${command.categoryId}/${payload.email}")
        return if (payload.categories?.contains(command.categoryId) == true) {
            listOf(createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(payload.setId(competitorId))))
        } else {
            throw IllegalArgumentException("Failed to get competitor from payload. Or competitor already exists")
        }
    }

    fun process(payload: UpdateCompetitorPayload, command: CommandDTO, createEvent: (command: CommandDTO, eventType: EventType, payload: Any?) -> EventDTO): List<EventDTO> {
        return listOf(createEvent(command, EventType.COMPETITOR_UPDATED, CompetitorUpdatedPayload(payload.competitor)))
    }

    fun process(payload: ChangeCompetitorCategoryPayload, com: CommandDTO, createEvent: (command: CommandDTO, eventType: EventType, payload: Any?) -> EventDTO): List<EventDTO> {
        val competitorId = payload.fighterId
        val newCategoryId = payload.newCategoryId
        return if (!newCategoryId.isNullOrBlank() && !competitorId.isNullOrBlank()) {
            listOf(createEvent(com, EventType.COMPETITOR_CATEGORY_CHANGED, payload))
        } else {
            throw IllegalArgumentException("New category or such competitor does not exist")
        }
    }
}