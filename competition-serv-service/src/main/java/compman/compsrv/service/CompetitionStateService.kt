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
import compman.compsrv.repository.RocksDBOperations
import compman.compsrv.service.processor.command.AggregateServiceFactory
import compman.compsrv.service.processor.command.AggregatesWithEvents
import compman.compsrv.service.processor.sagas.SagaExecutionService
import compman.compsrv.util.IDGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class CompetitionStateService(
    private val aggregateServiceFactory: AggregateServiceFactory,
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

    fun batchApply(events: List<EventDTO>, dbOperations: RocksDBOperations) {
        events.filter {
            log.info("Check if event is duplicate: $it")
            !duplicateCheck(it)
        }.fold(Unit) { _, eventHolder ->
            val start = System.currentTimeMillis()
            log.info("Batch applying start")
            apply(eventHolder, dbOperations, isBatch = true)
            val finishApply = System.currentTimeMillis()
            log.info("Batch apply finish, took ${Duration.ofMillis(finishApply - start)}. Starting flush")
            log.info("Flush finish, took ${Duration.ofMillis(System.currentTimeMillis() - finishApply)}.")
        }
    }


    fun apply(event: EventDTO, dbOperations: RocksDBOperations, isBatch: Boolean) {
        log.info("Applying event: $event, batch: $isBatch")
        val eventWithId = event.setId(event.id ?: IDGenerator.uid())
        if (isBatch || !duplicateCheck(event)) {
            aggregateServiceFactory.getAggregateService(event).getAggregate(event, dbOperations)
                .applyEvent(event, dbOperations)
            listOf(eventWithId)
        } else {
            throw EventApplyingException("Duplicate event: correlationId: ${eventWithId.correlationId}", eventWithId)
        }
    }

    fun process(command: CommandDTO, dbOperations: RocksDBOperations): AggregatesWithEvents<AbstractAggregate> {
        if (command.competitionId.isNullOrBlank()) {
            log.error("Competition id is empty, command $command")
            throw CommandProcessingException("Competition ID is empty.", command)
        }
        if (commandDedupCache.asMap().put(command.id, true) != null) {
            throw CommandProcessingException("Duplicate command.", command)
        }
        return when (AggregateTypeDecider.getCommandAggregateType(command.type)) {
            AggregateType.SAGA -> sagaExecutionService.executeSaga(command, dbOperations)
                .fold({ throw CommandProcessingException("Errors during saga execution: ${it.show()}", command) }, { it })
            else -> aggregateServiceFactory.getAggregateService(command)
                .processCommand(command, rocksDBOperations = dbOperations)
        }
    }
    fun duplicateCheck(event: EventDTO): Boolean = eventDedupCache.asMap().put(event.id, true) == null
}