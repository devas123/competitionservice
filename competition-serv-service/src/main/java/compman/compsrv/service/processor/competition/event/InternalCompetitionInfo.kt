package compman.compsrv.service.processor.competition.event

import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class InternalCompetitionInfo : IEventHandler<Competition> {
    override fun applyEvent(
            aggregate: Competition,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competition {
        return aggregate
    }

    override val eventType: EventType
        get() = EventType.INTERNAL_COMPETITION_INFO
}