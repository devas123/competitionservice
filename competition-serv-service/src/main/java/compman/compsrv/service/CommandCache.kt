package compman.compsrv.service

import com.google.common.cache.CacheBuilder
import compman.compsrv.model.events.EventDTO
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.CompletableFuture

@Component
class CommandCache {
    private val commands = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofSeconds(100))
            .maximumSize(100000).build<String, CompletableFuture<Array<EventDTO>>>()

    private val log = LoggerFactory.getLogger(CommandCache::class.java)

    fun executeCommand(correlationId: String, block: () -> Any): Mono<Array<EventDTO>> {
        return Mono.fromFuture(commands.get(correlationId) {
            block()
            CompletableFuture()
        })
    }

    fun commandCallback(correlationId: String, events: Array<EventDTO>) {
        log.info("CommandCallback $correlationId")
        commands.getIfPresent(correlationId)?.complete(events) ?: log.error("No callback handler for correlation id $correlationId")
    }

    fun waitForResult(correlationId: String, timeout: Duration): Array<out EventDTO> {
        return commands.getIfPresent(correlationId)?.let { cid ->
            Mono.fromFuture(cid).block(timeout)
        }.orEmpty()
    }
}