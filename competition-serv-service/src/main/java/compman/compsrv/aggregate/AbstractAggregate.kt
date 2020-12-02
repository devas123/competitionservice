package compman.compsrv.aggregate

import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.RocksDBOperations
import java.util.concurrent.atomic.AtomicLong

abstract class AbstractAggregate(protected val version: AtomicLong, protected val eventNumber: AtomicLong) {
    fun getVersion() = version.get()

    abstract fun applyEvent(eventDTO: EventDTO, rocksDBOperations: RocksDBOperations)
    abstract fun applyEvents(events: List<EventDTO>, rocksDBOperations: RocksDBOperations)

    fun enrichWithVersionAndNumber(v: Long, eventDTO: EventDTO): EventDTO {
        return eventDTO.setVersion(v).setLocalEventNumber(eventNumber.getAndIncrement())
    }
}