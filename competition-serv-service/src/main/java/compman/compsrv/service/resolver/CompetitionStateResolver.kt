package compman.compsrv.service.resolver

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.kafka.serde.EventDeserializer
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.dto.competition.CompetitionStateDTO
import compman.compsrv.model.dto.competition.CompetitionStateSnapshot
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.CompetitionStateCrudRepository
import compman.compsrv.service.CompetitionStateService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

@Component
class CompetitionStateResolver(private val kafkaProperties: KafkaProperties,
                               private val competitionStateService: CompetitionStateService,
                               private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                               private val clusterSesion: ClusterSession,
                               private val transactionTemplate: TransactionTemplate,
                               private val mapper: ObjectMapper) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(CompetitionStateResolver::class.java)
    }

    private fun consumerProperties() = Properties().apply {
        putAll(kafkaProperties.consumer.properties)
        setProperty(ConsumerConfig.GROUP_ID_CONFIG, "state-resolver-${UUID.randomUUID()}")
        setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.canonicalName)
        setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, EventDeserializer::class.java.canonicalName)
    }

    fun resolveLatestCompetitionState(competitionId: String, currentTimestamp: Long, globalStoreSnapshotSupplier: (competitionId: String) -> CompetitionStateSnapshot?) {
        log.info("Retrieving state for the competitionId: $competitionId")
        if (!clusterSesion.isProcessedLocally(competitionId)) {
            log.info("Competition $competitionId is not processed locally. Trying to get snapshot from the global state store.")
            val stateSnapshot = globalStoreSnapshotSupplier(competitionId)
            if (stateSnapshot != null) {
                log.info("Successfully found a snapshot for the competition $competitionId. Applying the events to get the latest state.")
                val consumer = KafkaConsumer<String, EventDTO>(consumerProperties())
                consumer.use { cons ->
                    val topicPartition = TopicPartition(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME, stateSnapshot.eventPartition)
                    cons.assign(listOf(topicPartition))
                    cons.seek(topicPartition, stateSnapshot.eventOffset)
                    val snapshot = mapper.readValue(stateSnapshot.serializedState, CompetitionStateDTO::class.java)
                    transactionTemplate.execute {
                        competitionStateCrudRepository.deleteById(snapshot.competitionId)
                        competitionStateCrudRepository.save(CompetitionState.fromDTO(snapshot))
                        var records: List<ConsumerRecord<String, EventDTO>>?
                        do {
                            log.info("Getting the events from the event log and applying them to the competition $competitionId")
                            val result = cons.poll(Duration.ofMillis(1000))
                            records = result?.records(topicPartition)?.filter { it.timestamp() <= currentTimestamp }
                                    ?: emptyList()
                            for (record in records.filter { it.key() == competitionId }) {
                                competitionStateService.apply(record.value())
                            }
                            cons.commitSync()
                        } while (!records.isNullOrEmpty())
                    }
                    log.info("Successfully retrieved state for the competition $competitionId")
                }
                clusterSesion.broadcastCompetitionProcessingInfo(setOf(competitionId))
            } else {
                log.error("Could not find state snapshot for $competitionId in the state store. This is very bad. Trying to find the 'COMPETITION_CREATED' event in the events for the past 30 days.")
                val consumer = KafkaConsumer<String, EventDTO>(consumerProperties())
                transactionTemplate.execute {
                    consumer.use { cons ->
                        val topicPartitions = cons.partitionsFor(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME).map { TopicPartition(it.topic(), it.partition()) }
                        val daysAgo = System.currentTimeMillis() - 30 * 24 * 3600 * 1000L
                        val assignment = cons.offsetsForTimes(topicPartitions.map { it to daysAgo }.toMap(), Duration.of(10, ChronoUnit.SECONDS))?.filter { it.value != null }
                        if (!assignment.isNullOrEmpty()) {
                            val assignedTopics = assignment.map { it.key }.toList()
                            cons.assign(assignedTopics)
                            assignment.forEach {
                                cons.seek(it.key, it.value.offset())
                            }
                            var records: List<ConsumerRecord<String, EventDTO>>?
                            var competitionCreated = false
                            do {
                                val result = cons.poll(Duration.of(10, ChronoUnit.SECONDS))
                                records = result?.records(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME)?.filter { it.timestamp() <= currentTimestamp }
                                        ?: emptyList()
                                if (!competitionCreated) {
                                    val createdEvent = records?.filter { it.key() == competitionId }?.find { it.value()?.type == EventType.COMPETITION_CREATED }
                                    if (createdEvent != null) {
                                        log.info("Yay! Found the 'COMPETITION_CREATED' event for $competitionId !")
                                        competitionCreated = true
                                        competitionStateService.apply(createdEvent.value())
                                        competitionStateService.batchApply(records?.filter { it.key() == competitionId && it.value()?.type != EventType.COMPETITION_CREATED }?.map { it.value() }?.toList()
                                                ?: emptyList())
                                    } else {
                                        log.error("Could not find the 'COMPETITION_CREATED' event for $competitionId, maybe the competition was created more than 30 days ago.")
                                        break
                                    }
                                } else {
                                    competitionStateService.batchApply(records?.filter { it.key() == competitionId }?.map { it.value() }?.toList()
                                            ?: emptyList())
                                }
                            } while (!records.isNullOrEmpty())
                            if (competitionCreated) {
                                log.info("We have initialized the state from the first event to the last for $competitionId. Fingers crossed")
                                clusterSesion.broadcastCompetitionProcessingInfo(setOf(competitionId))
                            } else {
                                log.error("Could not initialize the state for $competitionId")
                            }
                        }
                    }
                }
            }
        } else {
            log.info("Competition $competitionId is processed locally so the latest state is already in the database.")
        }
    }
}