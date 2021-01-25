package compman.compsrv.service.processor.event

import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations

interface IEventProcessor<AT : AbstractAggregate> {
    fun applyEvent(aggregate: AT, event: EventDTO, rocksDBOperations: DBOperations, save: Boolean = true): AT
    val eventType: EventType
}