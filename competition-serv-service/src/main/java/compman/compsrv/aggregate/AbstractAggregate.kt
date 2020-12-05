package compman.compsrv.aggregate

import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.DBOperations
import java.util.concurrent.atomic.AtomicLong

abstract class AbstractAggregate(private val version: AtomicLong, private val eventNumber: AtomicLong) {
    fun getVersion() = version.get()
    fun inctementVersion() = version.incrementAndGet()

    abstract fun applyEvent(eventDTO: EventDTO, rocksDBOperations: DBOperations)
    abstract fun applyEvents(events: List<EventDTO>, rocksDBOperations: DBOperations)

    fun enrichWithVersionAndNumber(v: Long, eventDTO: EventDTO): EventDTO {
        return eventDTO.setVersion(v).setLocalEventNumber(eventNumber.getAndIncrement())
    }
}