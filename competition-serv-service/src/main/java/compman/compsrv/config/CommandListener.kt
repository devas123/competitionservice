package compman.compsrv.config

import arrow.core.Left
import arrow.core.Option
import arrow.core.Right
import arrow.core.extensions.list.foldable.foldM
import arrow.core.getOrHandle
import arrow.fx.IO
import arrow.fx.extensions.io.monad.monad
import arrow.fx.fix
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.kafka.streams.transformer.CompetitionCommandTransformer
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.commands.CommandDTO
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
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.persistence.EntityManager

@Component
class CommandListener(private val commandTransformer: CompetitionCommandTransformer,
                      private val template: KafkaTemplate<String, EventDTO>,
                      private val transactionTemplate: TransactionTemplate,
                      private val eventRepository: EventRepository,
                      private val clusterSession: ClusterSession,
                      private val entityManager: EntityManager,
                      private val commandCache: CommandCache) : AcknowledgingConsumerAwareMessageListener<String, CommandDTO> {
    companion object {
        private val log = LoggerFactory.getLogger("commandProcessingLog")
    }

    @Transactional(propagation = Propagation.REQUIRED)
    override fun onMessage(data: ConsumerRecord<String, CommandDTO>, acknowledgment: Acknowledgment?, consumer: Consumer<*, *>?) {
        if (data.value() != null && data.key() != null) {
            log.info("Processing command: $data")
            val k = IO {
                commandTransformer.transform(data)
            }.flatMap { events ->
                val enventResults = events.filter {
                    when (it.type) {
                        EventType.ERROR_EVENT -> {
                            log.warn("Error event: $it")
                            false
                        }
                        EventType.DUMMY, EventType.INTERNAL_COMPETITION_INFO -> false
                        else -> true
                    }
                }
                IO {
                    log.info("All the events were processed. Sending commit offsets.")
                    eventRepository.saveAll(enventResults.map { it.toEntity() })
                    events
                }.flatMap {
                    val l = CountDownLatch(enventResults.size)
                    enventResults.foldM(IO.monad(), Option.empty<SendResult<String, EventDTO>>())
                    { _, event ->
                        IO.async { callback ->
                            template.send(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME, data.key(), event)
                                    .addCallback({
                                        l.countDown()
                                        callback.invoke(Right(Option.fromNullable(it)))
                                    }, { callback.invoke(Left(it)) })
                        }
                    }.fix().flatMap {
                        IO.async<Unit> { callback ->
                            if (l.await(10, TimeUnit.SECONDS)) {
                                callback.invoke(Right(Unit))
                            } else {
                                log.error("The events were not sent to the event log. The offsets of the incomming messages will not be committed.")
                                callback.invoke(Left(IllegalStateException("The events were not sent to the event log. The offsets of the incomming messages will not be committed.")))
                            }
                        }
                    }.flatMap { IO.just(events) }
                }.flatMap {
                    IO {
                        if (it.any { it.type == EventType.INTERNAL_COMPETITION_INFO }) {
                            log.info("Flushing database.")
                            entityManager.flush()
                        }
                        acknowledgment?.acknowledge()
                        it
                    }
                }
            }.flatMap { events ->
                val io = if (events.any { it.type == EventType.INTERNAL_COMPETITION_INFO }) {
                    IO {
                        log.info("Sending competition info to the cluster.")
                        clusterSession.broadcastCompetitionProcessingInfo(setOf(data.key()), data.value().correlationId)
                    }
                } else {
                    IO { commandCache.commandCallback(data.value().correlationId, events.toCollection(mutableListOf()).toTypedArray()) }
                }
                IO {
                    events.asSequence().onEach {
                        if (it.type == EventType.COMPETITION_DELETED) kotlin.runCatching {
                            clusterSession.broadcastCompetitionProcessingStopped(setOf(data.key()))
                        }
                        if (it.type == EventType.COMPETITION_CREATED) kotlin.runCatching {
                            clusterSession.broadcastCompetitionProcessingInfo(setOf(data.key()), data.value().correlationId)
                        }
                    }
                }.flatMap { io }
            }
            k.attempt().unsafeRunSync().getOrHandle { throw it }
        }
    }
}