package compman.compsrv.service.resolver

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.client.QueryServiceClient
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
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*

@Component
class CompetitionStateResolver(private val kafkaProperties: KafkaProperties,
                               private val competitionStateService: CompetitionStateService,
                               private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                               private val clusterSesion: ClusterSession,
                               private val mapper: ObjectMapper) {

    private fun consumerProperties() = Properties().apply {
        putAll(kafkaProperties.consumer.properties)
        setProperty(ConsumerConfig.GROUP_ID_CONFIG, "state-resolver-${UUID.randomUUID()}")
        setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.canonicalName)
        setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, EventDeserializer::class.java.canonicalName)
    }

    fun resolveLatestCompetitionState(competitionId: String, globalStoreSnapshotSupplier: (competitionId: String) -> CompetitionStateSnapshot?) {
        if (!clusterSesion.isProcessedLocally(competitionId)) {
            val stateSnapshot = globalStoreSnapshotSupplier(competitionId)
            if (stateSnapshot != null) {
                val consumer = KafkaConsumer<String, EventDTO>(consumerProperties())
                clusterSesion.broadcastCompetitionProcessingInfo(setOf(competitionId))
                consumer.use { cons ->
                    val topicPartition = TopicPartition(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME, stateSnapshot.eventPartition)
                    cons.assign(listOf(topicPartition))
                    cons.seek(topicPartition, stateSnapshot.eventOffset)
                    val now = System.currentTimeMillis()
                    val snapshot = mapper.readValue(stateSnapshot.serializedState, CompetitionStateDTO::class.java)
                    competitionStateCrudRepository.save(CompetitionState.fromDTO(snapshot))
                    while (true) {
                        val result = cons.poll(Duration.ofMillis(1000))
                        if (result != null) {
                            for (record in result.records(topicPartition).filter { it.key() == competitionId }) {
                                if (record.timestamp() <= now) {
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
                }
            }
        }
    }
}