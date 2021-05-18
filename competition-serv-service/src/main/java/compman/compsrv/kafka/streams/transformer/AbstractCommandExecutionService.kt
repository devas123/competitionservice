package compman.compsrv.kafka.streams.transformer

import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.repository.RocksDBRepository
import compman.compsrv.service.CommandSyncExecutor
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.processor.AbstractAggregateService.Companion.createErrorEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class AbstractCommandExecutionService(
    private val executionService: CompetitionStateService,
    private val clusterOperations: ClusterOperations,
    private val commandSyncExecutor: CommandSyncExecutor
) {


    private val log = LoggerFactory.getLogger(this.javaClass)


    open fun transform(command: CommandDTO, rocksDBRepository: RocksDBRepository,
                       kafkaTemplate: KafkaTemplate<String, EventDTO>,
                       eventsFilterPredicate: (EventDTO) -> Boolean): List<EventDTO>? {
        return rocksDBRepository.doInTransaction { rocksDBOperations ->
            kotlin.runCatching {
                val start = System.currentTimeMillis()
                log.info("Processing command: $command")
                val events = commandExecutionLogic(command, rocksDBOperations)
                log.info("Processing commands and applying events finished. Took ${Duration.ofMillis(System.currentTimeMillis() - start)}")
                val filteredEvents = events.filter(eventsFilterPredicate)
                val latch = CountDownLatch(filteredEvents.size)
                fun <T> callback() = { _: T -> latch.countDown() }
                filteredEvents.asSequence().forEach {
                    kafkaTemplate.send(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME, it.competitionId, it).addCallback(callback()) { ex ->
                        log.error("Exception when sending events to kafka.", ex)
                        throw ex
                    }
                }
                if (latch.await(300, TimeUnit.MILLISECONDS)) {
                    log.info("All the events were processed. Sending commit offsets.")
                    log.info("Executing post-processing.")
                    filteredEvents.asSequence().forEach {
                        if (it.type == EventType.COMPETITION_DELETED) kotlin.runCatching {
                            clusterOperations.broadcastCompetitionProcessingStopped(setOf(command.competitionId))
                        }
                        if (it.type == EventType.COMPETITION_CREATED) kotlin.runCatching {
                            clusterOperations.broadcastCompetitionProcessingInfo(setOf(command.competitionId), command.correlationId)
                        }
                    }
                    if (!command.correlationId.isNullOrBlank()) {
                        if (command.type == CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND) {
                            clusterOperations.broadcastCompetitionProcessingInfo(setOf(command.competitionId), command.correlationId)
                        } else {
                            commandSyncExecutor.commandCallback(command.correlationId, events.toTypedArray())
                        }
                    }
                    filteredEvents
                } else {
                    throw IllegalArgumentException("Not all events were sent to Kafka.")
                }
            }.getOrElse { exception ->
                log.error("Error while processing events.", exception)
                rocksDBOperations.rollback()
                listOf(createErrorEvent(command, exception.message))
            }
        }
    }


    private fun commandExecutionLogic(command: CommandDTO, rocksDBOperations: DBOperations): List<EventDTO> {
        return executionService.execute(command, rocksDBOperations)
    }
}