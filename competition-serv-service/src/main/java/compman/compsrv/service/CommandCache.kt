package compman.compsrv.service

import com.google.common.cache.CacheBuilder
import compman.compsrv.model.events.EventDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Component
class CommandCache {
    private val commands = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofSeconds(100))
            .maximumSize(100000).build<String, CompletableFuture<Array<EventDTO>>>()

    private val log = LoggerFactory.getLogger(CommandCache::class.java)

    fun executeCommand(correlationId: String, future: CompletableFuture<Array<EventDTO>>, block: () -> Any): CompletableFuture<Array<EventDTO>>? {
        return commands.get(correlationId) {
            log.info("Execute command $correlationId")
            block()
            future
        }
    }

    fun commandCallback(correlationId: String, events: Array<EventDTO>) {
        log.info("CommandCallback $correlationId")
        commands.getIfPresent(correlationId)?.complete(events) ?: log.trace("No callback handler for correlation id $correlationId")
    }

    fun waitForResult(correlationId: String, timeout: Duration): Array<EventDTO> {
        return commands.getIfPresent(correlationId)?.get(timeout.toMillis(), TimeUnit.MILLISECONDS) ?: emptyArray()
    }
}