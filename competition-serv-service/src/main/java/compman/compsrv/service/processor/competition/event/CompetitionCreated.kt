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
        aggregate: Competition?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Competition? = aggregate?.let {
        executeValidated<CompetitionCreatedPayload, Competition>(event) { payload, _ ->
            it.copy(
                id = event.competitionId,
                properties = payload.properties.setId(event.competitionId),
                registrationInfo = payload.reginfo.setId(event.competitionId)
            )
        }.unwrap(event)
    }

    override val eventType: EventType
        get() = EventType.COMPETITION_CREATED
}