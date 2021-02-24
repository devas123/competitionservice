package compman.compsrv.service.processor

import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations

interface IEventHandler<AT : AbstractAggregate> {
    fun applyEvent(aggregate: AT?, event: EventDTO, rocksDBOperations: DBOperations): AT?
    val eventType: EventType
}