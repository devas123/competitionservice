package compman.compsrv.service.processor.competition.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitionCreatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class CompetitionStopped(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : CompetitionStatusUpdated(mapper, validators) {
    override val eventType: EventType
        get() = EventType.COMPETITION_STOPPED
}