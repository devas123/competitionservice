package compman.compsrv.service.processor.event

import compman.compsrv.model.events.EventDTO

interface IEventExecutionEffects {
    fun effects(event: EventDTO): List<EventDTO> = emptyList()
}