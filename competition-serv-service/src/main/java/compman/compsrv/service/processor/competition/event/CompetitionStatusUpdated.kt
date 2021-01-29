package compman.compsrv.service.processor.competition.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitionPropertiesUpdatedPayload
import compman.compsrv.model.events.payload.CompetitionStatusUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.applyProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

abstract class CompetitionStatusUpdated(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Competition>, ValidatedEventExecutor<Competition>(mapper, validators) {
    override fun applyEvent(
            aggregate: Competition,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competition {
        return executeValidated<CompetitionStatusUpdatedPayload, Competition>(event) { payload, _ ->
            aggregate.statusUpdated(payload)
        }.unwrap(event)
    }

    fun Competition.statusUpdated(payload: CompetitionStatusUpdatedPayload): Competition {
        this.properties.status = payload.status
        return this
    }
}