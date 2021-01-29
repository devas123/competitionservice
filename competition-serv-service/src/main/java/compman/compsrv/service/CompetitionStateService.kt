package compman.compsrv.service

import com.google.common.cache.CacheBuilder
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.aggregate.AggregateType
import compman.compsrv.aggregate.AggregateTypeDecider
import compman.compsrv.errors.show
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.exceptions.CommandProcessingException
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.DelegatingAggregateService
import compman.compsrv.service.processor.saga.SagaExecutionService
import compman.compsrv.util.IDGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CompetitionStateService(
    private val delegatingAggregateService: DelegatingAggregateService,
    private val sagaExecutionService: SagaExecutionService
) {

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionStateService::class.java)
    }

    private val commandDedupCache =
        CacheBuilder.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofSeconds(10))
            .concurrencyLevel(Runtime.getRuntime().availableProcessors()).weakValues().build<String, Boolean>()

    private val eventDedupCache = CacheBuilder.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofSeconds(10))
        .concurrencyLevel(Runtime.getRuntime().availableProcessors()).weakValues().build<String, Boolean>()

    fun <AG : AbstractAggregate> batchApply(aggregate: AG, events: List<EventDTO>, dbOperations: DBOperations): AG {
        log.info("Batch applying start")
        val start = System.currentTimeMillis()
        val result = events.filter {
            log.info("Check if event is duplicate: $it")
            !duplicateCheck(it)
        }.fold(aggregate) { agg, eventHolder ->
            val newAgg = apply(agg, eventHolder, dbOperations, isBatch = true)
            newAgg
        }
        val finishApply = System.currentTimeMillis()
        log.info("Batch apply finish, took ${Duration.ofMillis(finishApply - start)}. Starting flush")
        log.info("Flush finish, took ${Duration.ofMillis(System.currentTimeMillis() - finishApply)}.")
        return result
    }


    @Suppress("UNCHECKED_CAST")
    fun <AG : AbstractAggregate> apply(
        aggregate: AG,
        event: EventDTO,
        dbOperations: DBOperations,
        isBatch: Boolean
    ): AG {
        log.info("Applying event: $event, batch: $isBatch")
        val eventWithId = event.apply { id = event.id ?: IDGenerator.uid() }
        return if (isBatch || !duplicateCheck(event)) {
            delegatingAggregateService.applyEvent(aggregate, event, dbOperations) as AG
        } else {
            throw EventApplyingException("Duplicate event: correlationId: ${eventWithId.correlationId}", eventWithId)
        }
    }

    fun execute(command: CommandDTO, dbOperations: DBOperations): List<EventDTO> {
        if (command.competitionId.isNullOrBlank()) {
            log.error("Competition id is empty, command $command")
            throw CommandProcessingException("Competition ID is empty.", command)
        }
        if (commandDedupCache.asMap().put(command.id, true) != null) {
            throw CommandProcessingException("Duplicate command.", command)
        }
        return when (AggregateTypeDecider.getCommandAggregateType(command.type)) {
            AggregateType.SAGA -> sagaExecutionService.executeSaga(command, dbOperations)
                .fold({
                    log.error("Errors during saga execution: ${it.show()}")
                    throw CommandProcessingException("Errors during saga execution.", command)
                }, { it })
            else -> {
                val results = delegatingAggregateService.getAggregateService(command)
                    .processCommand(command, rocksDBOperations = dbOperations)
                delegatingAggregateService.applyEvents(results.first, results.second, dbOperations)
                return results.second
            }
        }
    }

    fun duplicateCheck(event: EventDTO): Boolean = eventDedupCache.asMap().put(event.id, true) == null
}