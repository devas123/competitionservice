package compman.compsrv.service.processor.event

import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType

interface IEventProcessor {
    fun affectedEvents(): Set<EventType>
    fun applyEvent(event: EventDTO)
}