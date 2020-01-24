package compman.compsrv.config

import compman.compsrv.cluster.ClusterSession
import compman.compsrv.kafka.streams.transformer.CompetitionCommandTransformer
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.EventRepository
import compman.compsrv.service.CommandCache
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.AcknowledgingConsumerAwareMessageListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManager

@Component
class CommandListener(private val commandTransformer: CompetitionCommandTransformer,
                      private val template: KafkaTemplate<String, EventDTO>,
                      private val eventRepository: EventRepository,
                      private val clusterSession: ClusterSession,
                      private val entityManager: EntityManager,
                      private val commandCache: CommandCache) : AcknowledgingConsumerAwareMessageListener<String, CommandDTO> {
    companion object {
        private val log = LoggerFactory.getLogger("commandProcessingLog")
    }

    fun handleMessage(m: ConsumerRecord<String, CommandDTO>, acknowledgment: Acknowledgment?, consumer: Consumer<*, *>?): List<EventDTO> {
        if (m.value() != null && m.key() != null) {
            log.info("Processing command: $m")
            val events = commandTransformer.transform(m)
            val filteredEvents = events.filter {
                when (it.type) {
                    EventType.ERROR_EVENT -> {
                        log.warn("Error event: $it")
                        false
                    }
                    EventType.DUMMY, EventType.INTERNAL_COMPETITION_INFO -> false
                    else -> true
                }
            }
            eventRepository.saveAll(events.map { it.toEntity() })
            entityManager.flush()
            log.info("Executing post-processing.")
            filteredEvents.asSequence().forEach {
                if (it.type == EventType.COMPETITION_DELETED) kotlin.runCatching {
                    clusterSession.broadcastCompetitionProcessingStopped(setOf(m.key()))
                }
                if (it.type == EventType.COMPETITION_CREATED) kotlin.runCatching {
                    clusterSession.broadcastCompetitionProcessingInfo(setOf(m.key()), m.value().correlationId)
                }
            }
            if (!m.value().correlationId.isNullOrBlank()) {
                if (m.value().type == CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND) {
                    clusterSession.broadcastCompetitionProcessingInfo(setOf(m.key()), m.value().correlationId)
                } else {
                    commandCache.commandCallback(m.value().correlationId, events.toTypedArray())
                }
            }
            return filteredEvents
        }
        return emptyList()
    }

    override fun onMessage(m: ConsumerRecord<String, CommandDTO>, acknowledgment: Acknowledgment?, consumer: Consumer<*, *>?) {
        val filteredEvents = handleMessage(m, acknowledgment, consumer)
        val latch = CountDownLatch(filteredEvents.size)
        fun <T> callback() = { _: T -> latch.countDown() }
        filteredEvents.asSequence().forEach {
            template.send(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME, m.key(), it).addCallback(callback(), { ex ->
                log.error("Exception when sending events to kafka.", ex)
                throw ex
            })
        }
        if (latch.await(10, TimeUnit.SECONDS)) {
            log.info("All the events were processed. Sending commit offsets.")
            acknowledgment?.acknowledge()
        } else {
            log.error("The events were not sent to the event log. The offsets of the incomming messages will not be committed.")
            throw IllegalStateException("The events were not sent to the event log. The offsets of the incomming messages will not be committed.")
        }
    }
}