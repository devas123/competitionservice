package compman.compsrv.service

import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.REQUIRED)
interface ICommandProcessingService<CommandType, EventType> {

    fun apply(event: EventType, isBatch: Boolean = false): List<EventType>

    fun batchApply(events: List<EventType>): List<EventType> {
        return events.filter { !duplicateCheck(it) }.fold(emptyList()) { acc, eventHolder ->
            val res = (acc + apply(eventHolder, isBatch = true))
            flush()
            res
        }
    }

    fun flush()

    fun duplicateCheck(event: EventType): Boolean

    fun process(command: CommandType): List<EventType>
}