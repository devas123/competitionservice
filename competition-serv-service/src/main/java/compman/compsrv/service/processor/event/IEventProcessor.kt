package compman.compsrv.service.processor.event

import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType

interface IEventProcessor<State> {
    fun affectedEvents(): Set<EventType>
    fun applyEvent(state: State, event: EventDTO): Pair<State, List<EventDTO>>
}