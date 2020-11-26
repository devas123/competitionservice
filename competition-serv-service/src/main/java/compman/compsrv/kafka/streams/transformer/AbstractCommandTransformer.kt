package compman.compsrv.kafka.streams.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.service.CommandSyncExecutor
import compman.compsrv.service.ICommandProcessingService
import compman.compsrv.service.processor.event.IEventExecutionEffects
import compman.compsrv.util.createErrorEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.lang.IllegalArgumentException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class AbstractCommandTransformer(
        private val executionService: ICommandProcessingService<CommandDTO, EventDTO>,
        private val eventExecutionEffects: IEventExecutionEffects,
        private val clusterOperations: ClusterOperations,
        private val commandSyncExecutor: CommandSyncExecutor,
        private val mapper: ObjectMapper) {


    private val log = LoggerFactory.getLogger(this.javaClass)


    open fun transform(m: ConsumerRecord<String, CommandDTO>, transactionTemplate: TransactionTemplate,
                       kafkaTemplate: KafkaTemplate<String, EventDTO>,
                       eventsFilterPredicate: (EventDTO) -> Boolean): List<EventDTO>? {
        val command = m.value()
        return transactionTemplate.execute { status ->
            kotlin.runCatching {
                val start = System.currentTimeMillis()
                log.info("Processing command: $command")
                val events = commandExecutionLogic(command)
                log.info("Processing commands and applying events finished. Took ${Duration.ofMillis(System.currentTimeMillis() - start)}")
                val filteredEvents = events.filter(eventsFilterPredicate)
                val latch = CountDownLatch(filteredEvents.size)
                fun <T> callback() = { _: T -> latch.countDown() }
                filteredEvents.asSequence().forEach {
                    kafkaTemplate.send(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME, it.competitionId, it).addCallback(callback(), { ex ->
                        log.error("Exception when sending events to kafka.", ex)
                        throw ex
                    })
                }
                if (latch.await(10, TimeUnit.SECONDS)) {
                    log.info("All the events were processed. Sending commit offsets.")
                    log.info("Executing post-processing.")
                    filteredEvents.asSequence().forEach {
                        if (it.type == EventType.COMPETITION_DELETED) kotlin.runCatching {
                            clusterOperations.broadcastCompetitionProcessingStopped(setOf(m.key()))
                        }
                        if (it.type == EventType.COMPETITION_CREATED) kotlin.runCatching {
                            clusterOperations.broadcastCompetitionProcessingInfo(setOf(m.key()), command.correlationId)
                        }
                    }
                    if (!command.correlationId.isNullOrBlank()) {
                        if (command.type == CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND) {
                            clusterOperations.broadcastCompetitionProcessingInfo(setOf(m.key()), command.correlationId)
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
                status.setRollbackOnly()
                createErrorEvent(command, exception.message)
            }
        }
    }

    private fun createErrorEvent(command: CommandDTO, message: String?) = listOf(mapper.createErrorEvent(command, message))


    private fun commandExecutionLogic(command: CommandDTO): List<EventDTO> {
        val validationErrors = canExecuteCommand(command)
        return when {
            validationErrors.isEmpty() -> {
                log.info("Command validated: $command")
                val eventsToApply = executionService.process(command)
                eventLoop(emptyList(), eventsToApply)
            }
            else -> {
                log.error("Command not valid: ${validationErrors.joinToString(separator = ",")}")
                createErrorEvent(command, validationErrors.joinToString(separator = ","))
            }
        }

    }

    private tailrec fun eventLoop(appliedEvents: List<EventDTO>, eventsToApply: List<EventDTO>): List<EventDTO> = when {
        eventsToApply.isEmpty() -> appliedEvents
        eventsToApply.any { it.type == EventType.ERROR_EVENT } -> {
            log.info("There were errors while processing the command. Returning error events.")
            appliedEvents + eventsToApply.filter { it.type == EventType.ERROR_EVENT }
        }
        else -> {
            log.info("Applying generated events: ${eventsToApply.joinToString("\n")}")
            executionService.batchApply(eventsToApply)
            val effects = eventsToApply.flatMap(eventExecutionEffects::effects)
            if (effects.any { it.type == EventType.ERROR_EVENT }) {
                log.info("There were errors in the effects. Returning applied events.")
                appliedEvents + eventsToApply
            } else {
                eventLoop(appliedEvents + eventsToApply, effects)
            }
        }
    }

    open fun canExecuteCommand(command: CommandDTO?): List<String> = emptyList()
}