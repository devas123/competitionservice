package compman.compsrv.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

interface ICommandProcessingService<Command, Event> {
    companion object {
        val log: Logger = LoggerFactory.getLogger(ICommandProcessingService::class.java)
    }

    fun apply(event: Event, isBatch: Boolean = false)

    @Transactional(propagation = Propagation.REQUIRED)
    fun batchApply(events: List<Event>) {
        events.filter {
            log.info("Check if event is duplicate: $it")
            !duplicateCheck(it)
        }.fold(Unit) { _, eventHolder ->
            val start = System.currentTimeMillis()
            log.info("Batch applying start")
            apply(eventHolder, isBatch = true)
            val finishApply = System.currentTimeMillis()
            log.info("Batch apply finish, took ${Duration.ofMillis(finishApply - start)}. Starting flush")
            log.info("Flush finish, took ${Duration.ofMillis(System.currentTimeMillis() - finishApply)}.")
        }
    }

    fun duplicateCheck(event: Event): Boolean

    fun process(command: Command): List<Event>
}