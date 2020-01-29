package compman.compsrv.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

@Transactional(propagation = Propagation.REQUIRED)
interface ICommandProcessingService<CommandType, EventType> {
    companion object {
        val log: Logger = LoggerFactory.getLogger(ICommandProcessingService::class.java)
    }

    fun apply(event: EventType, isBatch: Boolean = false): List<EventType>

    fun batchApply(events: List<EventType>): List<EventType> {
        return events.filter { !duplicateCheck(it) }.fold(emptyList()) { acc, eventHolder ->
            val start = System.currentTimeMillis()
            log.info("Batch applying start")
            val res = (acc + apply(eventHolder, isBatch = true))
            val finishApply = System.currentTimeMillis()
            log.info("Batch apply finish, took ${Duration.ofMillis(finishApply - start)}. Starting flush")
            flush()
            log.info("Flush finish, took ${Duration.ofMillis(System.currentTimeMillis() - finishApply)}.")
            res
        }
    }

    fun flush()

    fun duplicateCheck(event: EventType): Boolean

    fun process(command: CommandType): List<EventType>
}