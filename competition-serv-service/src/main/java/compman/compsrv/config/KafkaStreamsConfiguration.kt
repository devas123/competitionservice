package compman.compsrv.config

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.kafka.serde.CommandSerializer
import compman.compsrv.kafka.streams.CompetitionProcessingStreamsBuilderFactory
import compman.compsrv.kafka.streams.CompetitionProcessingStreamsBuilderFactory.Companion.COMPETITION_STATE_SNAPSHOT_STORE_NAME
import compman.compsrv.kafka.streams.LeaderProcessStreams
import compman.compsrv.kafka.streams.MetadataService
import compman.compsrv.kafka.streams.processor.CompetitionCommandTransformer
import compman.compsrv.kafka.streams.processor.StateSnapshotForwardingProcessor
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.CommandCrudRepository
import compman.compsrv.repository.CompetitionStateRepository
import compman.compsrv.repository.EventCrudRepository
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.resolver.CompetitionStateResolver
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.ValueTransformerWithKeySupplier
import org.apache.kafka.streams.processor.ProcessorSupplier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
class KafkaStreamsConfiguration {

    @Bean(destroyMethod = "close")
    fun adminClient(props: KafkaProperties): KafkaAdminUtils {
        val adminProps = Properties()
        adminProps[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = props.bootstrapServers
        return KafkaAdminUtils(adminProps)
    }

    @Bean(destroyMethod = "close")
    fun kafkaProducer(props: KafkaProperties): KafkaProducer<String, CommandDTO> {
        val producerProps = Properties().apply {
            putAll(props.producer.properties)
        }
        producerProps[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        producerProps[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = CommandSerializer::class.java.canonicalName
        producerProps[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        producerProps[ProducerConfig.RETRIES_CONFIG] = 10
        return KafkaProducer(producerProps)
    }

    @Bean
    fun commandTransformer(competitionStateService: CompetitionStateService,
                           mapper: ObjectMapper,
                           competitionStateRepository: CompetitionStateRepository,
                           competitionStateResolver: CompetitionStateResolver,
                           clusterSession: ClusterSession) = ValueTransformerWithKeySupplier<String, CommandDTO, List<EventDTO>> {
        CompetitionCommandTransformer(competitionStateService,
                competitionStateRepository,
                competitionStateResolver,
                mapper,
                COMPETITION_STATE_SNAPSHOT_STORE_NAME)
    }

    @Bean
    fun snapshotEventsProcessor(commandCrudRepository: CommandCrudRepository, eventCrudRepository: EventCrudRepository, clusterSession: ClusterSession, mapper: ObjectMapper) = ProcessorSupplier<String, EventDTO> {
        StateSnapshotForwardingProcessor(COMPETITION_STATE_SNAPSHOT_STORE_NAME, commandCrudRepository, eventCrudRepository, clusterSession, mapper)
    }

    @Bean
    fun streamProcessingTopology(
            eventProcessor: ProcessorSupplier<String, EventDTO>,
            commandTransformer: ValueTransformerWithKeySupplier<String, CommandDTO, List<EventDTO>>,
            mapper: ObjectMapper,
            adminUtils: KafkaAdminUtils,
            props: KafkaProperties, clusterSession: ClusterSession): StreamsBuilder {
        return CompetitionProcessingStreamsBuilderFactory(
                CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME,
                CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME,
                commandTransformer,
                eventProcessor,
                adminUtils, props, mapper, clusterSession).createBuilder()
    }

    @Bean(destroyMethod = "close")
    fun streams(builder: StreamsBuilder, kafkaProperties: KafkaProperties): KafkaStreams {
        val streamProperties = Properties().apply { putAll(kafkaProperties.streamProperties) }
        return KafkaStreams(builder.build(), streamProperties)
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    fun competitionProcessStream(kafkaStreams: KafkaStreams, adminUtils: KafkaAdminUtils) = LeaderProcessStreams(adminUtils, kafkaStreams)

    @Bean
    fun metadataService(streams: KafkaStreams) = MetadataService(streams)
}