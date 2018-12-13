package compman.compsrv.kafka.streams


import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.kafka.serde.CommandSerde
import compman.compsrv.kafka.serde.EventSerde
import compman.compsrv.kafka.serde.JsonSerde
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.competition.CompetitionStateSnapshot
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier
import org.apache.kafka.streams.processor.ProcessorSupplier
import org.apache.kafka.streams.state.Stores


class CompetitionProcessingStreamsBuilderFactory(
        private val competitionCommandsTopic: String,
        private val competitionEventsTopic: String,
        private val commandTransformer: ValueTransformerWithKeySupplier<String, Command, List<EventHolder>>,
        private val snapshotEventsProcessor: ProcessorSupplier<String, EventHolder>,
        adminClient: KafkaAdminUtils,
        kafkaProperties: KafkaProperties,
        private val mapper: ObjectMapper,
        private val clusterSession: ClusterSession) {

    companion object {
        const val COMPETITION_STATE_SNAPSHOT_STORE_NAME = "competition_state_snapshot_store"
        const val ROUTING_METADATA_KEY = "routing"
    }


    init {
        adminClient.createTopicIfMissing(competitionCommandsTopic, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        adminClient.createTopicIfMissing(competitionEventsTopic, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        adminClient.createTopicIfMissing(CompetitionServiceTopics.COMPETITION_STATE_SNAPSHOTS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor, compacted = true)
    }

    fun createBuilder()
            : StreamsBuilder {
        val builder = StreamsBuilder()
        val keyValueStoreBuilder = Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(COMPETITION_STATE_SNAPSHOT_STORE_NAME),
                Serdes.String(),
                JsonSerde(CompetitionStateSnapshot::class.java, mapper))
        builder.addGlobalStore(keyValueStoreBuilder, competitionEventsTopic, Consumed.with(Serdes.String(), EventSerde()), snapshotEventsProcessor)
        val allCommands = builder.stream<String, Command>(competitionCommandsTopic, Consumed.with(Serdes.String(), CommandSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))
                .filter { key, value -> value != null && !key.isNullOrBlank() }

        //Process commands
        allCommands
                .transformValues(commandTransformer, COMPETITION_STATE_SNAPSHOT_STORE_NAME)
                .flatMapValues { value -> value }
                .filterNot { _, value -> value == null || value.type == EventType.DUMMY }.to({ _, event, _ -> KafkaAdminUtils.getEventRouting(event, competitionEventsTopic) }, Produced.with(Serdes.String(), EventSerde()))

        //Broadcast competitionIds that this instance is processing
        val grouped = builder.table<String, Command>(competitionCommandsTopic, Consumed.with(Serdes.String(), CommandSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))
        grouped.toStream().foreach { key, _ -> clusterSession.broadcastCompetitionProcessingInfo(setOf(key)) }

        return builder
    }

}