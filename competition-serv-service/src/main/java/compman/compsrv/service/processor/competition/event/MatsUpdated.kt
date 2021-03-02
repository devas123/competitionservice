package compman.compsrv.service.processor.competition.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.MatsUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class MatsUpdated(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Competition>, ValidatedEventExecutor<Competition>(mapper, validators) {
    override fun applyEvent(
            aggregate: Competition?,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Competition? = aggregate?.let {
        executeValidated<MatsUpdatedPayload, Competition>(event) { payload, _ ->
            aggregate.numberOfFightsOnTheMatChanged(payload)
        }.unwrap(event)
    }

    fun Competition.numberOfFightsOnTheMatChanged(payload: MatsUpdatedPayload): Competition {
        val updatedMats = payload.mats.groupBy { it.id }.mapValues { it.value.first() }
        mats.forEach { m ->
            updatedMats[m.id]?.let { upd ->
                upd.name?.let { m.name = it }
                upd.matOrder?.let { m.matOrder = it }
                upd.periodId?.let { m.periodId = it }
                upd.numberOfFights?.let { m.numberOfFights = it }
            }
        }
        return this
    }

    override val eventType: EventType
        get() = EventType.MATS_UPDATED
}