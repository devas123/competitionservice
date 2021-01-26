package compman.compsrv.service.processor.competition.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitionCreatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class CompetitionCreated(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Competition>, ValidatedEventExecutor<Competition>(mapper, validators) {
    override fun applyEvent(
            aggregate: Competition,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competition {
        return executeValidated<CompetitionCreatedPayload, Competition>(event) { payload, _ ->
            aggregate.competitionCreated(payload)
        }.unwrap(event)
    }

    fun Competition.competitionCreated(payload: CompetitionCreatedPayload): Competition {
        return this.copy(id = payload.properties.id, properties = payload.properties, registrationInfo = payload.reginfo)
    }

    override val eventType: EventType
        get() = EventType.COMPETITION_CREATED
}