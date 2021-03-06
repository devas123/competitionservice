package compman.compsrv.service.resolver

import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.kafka.serde.EventDeserializer
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.CompetitionCleaner
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.util.IDGenerator
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

@Component
class CompetitionStateResolver(private val kafkaProperties: ObjectProvider<KafkaProperties>,
                               private val competitionStateService: CompetitionStateService,
                               private val clusterSesion: ObjectProvider<ClusterOperations>,
                               private val competitionCleaner: CompetitionCleaner) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(CompetitionStateResolver::class.java)
    }

    private fun consumerProperties() = Properties().apply {
        kafkaProperties.ifAvailable?.buildConsumerProperties()?.let(this::putAll)
        setProperty(ConsumerConfig.GROUP_ID_CONFIG, "state-resolver-${IDGenerator.uid()}")
        setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.canonicalName)
        setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, EventDeserializer::class.java.canonicalName)
    }

    fun resolveLatestCompetitionState(competitionId: String, dbOperations: DBOperations) {
        log.info("Retrieving state for the competitionId: $competitionId")
        if (clusterSesion.ifAvailable?.isProcessedLocally(competitionId) == false) {
            log.error("Trying to find the 'COMPETITION_CREATED' event in the events for the past 365 days.")
            val competitionCreated = initStateAndSendCommand(competitionId, dbOperations)
            if (competitionCreated) {
                log.info("We have initialized the state from the first event to the last for $competitionId")
            } else {
                log.error("Could not initialize the state for $competitionId")
            }
        } else {
            log.info("Competition $competitionId is processed locally so the latest state is already in the database.")
        }
    }

    private fun initStateAndSendCommand(competitionId: String, dbOperations: DBOperations): Boolean {
        val consumer = KafkaConsumer<String, EventDTO>(consumerProperties())
        var competitionCreated = false
        consumer.use { cons ->
            val topicPartitions = cons.partitionsFor(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME).map { TopicPartition(it.topic(), it.partition()) }
            val daysAgo = System.currentTimeMillis() - Duration.ofDays(365).toMillis()
            val assignment = cons.offsetsForTimes(topicPartitions.map { it to daysAgo }.toMap(), Duration.of(10, ChronoUnit.SECONDS))?.filter { it.value != null }
            if (!assignment.isNullOrEmpty()) {
                val assignedTopics = assignment.map { it.key }.toList()
                cons.assign(assignedTopics)
                assignment.forEach {
                    cons.seek(it.key, it.value.offset())
                }
                var records: List<ConsumerRecord<String, EventDTO>>?
                do {
                    val result = cons.poll(Duration.of(200, ChronoUnit.MILLIS))
                    records = result?.records(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME)?.toList()
                            .orEmpty()
                    if (!competitionCreated) {
                        val recSequence = records.asSequence()
                                .filter { it.key() == competitionId }
                        val createdEvent = recSequence
                                .find { it.value()?.type == EventType.COMPETITION_CREATED }
                        if (createdEvent != null) {
                            log.info("Yay! Found the 'COMPETITION_CREATED' event for $competitionId !")
                            competitionCleaner.deleteCompetition(competitionId)
                            competitionStateService.apply(createdEvent.value(), dbOperations, false)
                            competitionCreated = true
                            log.info("Finished applying 'COMPETITION_CREATED' event for $competitionId")
                            val events = recSequence.filter { it.value()?.type != EventType.COMPETITION_CREATED }.map { it.value() }.toList()
                            log.debug("Applying batch events: ${events.joinToString("\n")}")
                            competitionStateService.batchApply(events, dbOperations)
                        } else {
                            log.error("Could not find the 'COMPETITION_CREATED' event for $competitionId, maybe the competition was created more than 365 days ago.")
                            break
                        }
                    } else {
                        val events = records.filter { it.key() == competitionId }.map { it.value() }.toList()
                        log.info("Applying batch events: ${events.joinToString("\n")}")
                        competitionStateService.batchApply(events, dbOperations)
                    }
                    cons.commitSync()
                } while (!records.isNullOrEmpty())
            }
        }
        return competitionCreated
    }
}