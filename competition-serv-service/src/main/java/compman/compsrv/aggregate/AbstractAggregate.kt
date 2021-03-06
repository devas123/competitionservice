package compman.compsrv.aggregate

import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.DBOperations
import java.util.concurrent.atomic.AtomicLong

abstract class AbstractAggregate(private val version: AtomicLong, private val eventNumber: AtomicLong) {
    fun version() = version.get()
    fun inctementVersion() = version.incrementAndGet()
    fun inctementVersionBy(amount: Int) = version.addAndGet(amount.toLong())

    fun enrichWithVersionAndNumber(v: Long, eventDTO: EventDTO): EventDTO {
        return eventDTO.setVersion(v).setLocalEventNumber(eventNumber.getAndIncrement())
    }
}