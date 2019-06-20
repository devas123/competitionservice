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
import compman.compsrv.repository.CompetitionStateCrudRepository
import compman.compsrv.service.CompetitionStateService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*

@Component
class CompetitionStateResolver(private val kafkaProperties: KafkaProperties,
                               private val competitionStateService: CompetitionStateService,
                               private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                               private val clusterSesion: ClusterSession,
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

    fun resolveLatestCompetitionState(competitionId: String, timestamp: Long, globalStoreSnapshotSupplier: (competitionId: String) -> CompetitionStateSnapshot?) {
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
                    competitionStateCrudRepository.save(CompetitionState.fromDTO(snapshot))
                    while (true) {
                        log.info("Getting the events from the event log and applying them to the competition $competitionId")
                        val result = cons.poll(Duration.ofMillis(1000))
                        if (result != null && !result.isEmpty) {
                            for (record in result.records(topicPartition).filter { it.key() == competitionId }) {
                                if (record.timestamp() <= timestamp) {
                                    competitionStateService.apply(record.value())
                                } else {
                                    break
                                }
                            }
                        } else {
                            break
                        }
                        cons.commitSync()
                    }
                    log.info("Successfully retrieved state for the competition $competitionId")
                }
                clusterSesion.broadcastCompetitionProcessingInfo(setOf(competitionId))
            }
        } else {
            log.info("Competition $competitionId is processed locally so the latest state is already in the database.")
        }
    }
}