package compman.compsrv.service.resolver

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.client.QueryServiceClient
import compman.compsrv.kafka.serde.EventDeserializer
import compman.compsrv.kafka.streams.CompetitionProcessingStreamsBuilderFactory
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.competition.CompetitionState
import compman.compsrv.model.competition.CompetitionStateSnapshot
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.CompetitionStateSnapshotService
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*

@Component
class CompetitionStateResolver(private val queryServiceClient: QueryServiceClient,
                               private val kafkaProperties: KafkaProperties,
                               private val competitionStateSnapshotService: CompetitionStateSnapshotService,
                               private val competitionStateService: CompetitionStateService,
                               streams: KafkaStreams,
                               private val mapper: ObjectMapper) {

    private val stateStore = streams.store(CompetitionProcessingStreamsBuilderFactory.COMPETITION_STATE_SNAPSHOT_STORE_NAME, QueryableStoreTypes.keyValueStore<String, CompetitionStateSnapshot>())

    private val consumerProperties = Properties().apply {
        putAll(kafkaProperties.consumer.properties)
        setProperty(ConsumerConfig.GROUP_ID_CONFIG, "state-resolver-${UUID.randomUUID()}")
        setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.canonicalName)
        setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, EventDeserializer::class.java.canonicalName)
    }

    fun resolveLatestCompetitionState(competitionId: String): Optional<CompetitionState> {
        val localStateSnapshot = Optional.ofNullable(stateStore.get(competitionId))
        val stateSnapshot = localStateSnapshot.orElseGet { competitionStateSnapshotService.getSnapshot(competitionId).orElse(queryServiceClient.getCompetitionStateSnapshot(competitionId)) }
        return if (stateSnapshot != null) {
            val consumer = KafkaConsumer<String, EventHolder>(consumerProperties)
            consumer.use { cons ->
                val topicPartition = TopicPartition(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME, stateSnapshot.eventPartition)
                cons.assign(listOf(topicPartition))
                cons.seek(topicPartition, stateSnapshot.eventOffset)
                val now = System.currentTimeMillis()
                var lastOffset = stateSnapshot.eventOffset
                var snapshot = mapper.readValue(stateSnapshot.state, CompetitionState::class.java)
                while (true) {
                    val result = cons.poll(Duration.ofMillis(1000))
                    if (result != null) {
                        for (record in result.records(topicPartition).filter { it.key() == competitionId }) {
                            if (record.timestamp() <= now) {
                                snapshot = competitionStateService.apply(record.value(), snapshot).first ?: snapshot
                                lastOffset = record.offset()
                            } else {
                                break
                            }
                        }
                    } else {
                        break
                    }
                    cons.commitSync()
                }
                competitionStateSnapshotService.saveSnapshot(CompetitionStateSnapshot(null, competitionId, stateSnapshot.eventPartition, lastOffset, mapper.writeValueAsBytes(snapshot)))
                Optional.of(snapshot)
            }
        } else {
            Optional.empty()
        }
    }
}