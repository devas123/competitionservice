package compman.compsrv.config

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.kafka.streams.CompetitionProcessingStreamsBuilderFactory
import compman.compsrv.kafka.streams.CompetitionProcessingStreamsBuilderFactory.Companion.COMPETITION_STATE_SNAPSHOT_STORE_NAME
import compman.compsrv.kafka.streams.LeaderProcessStreams
import compman.compsrv.kafka.streams.processor.CompetitionCommandTransformer
import compman.compsrv.kafka.streams.processor.StateSnapshotForwardingProcessor
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.repository.CompetitionStateRepository
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.resolver.CompetitionStateResolver
import org.apache.kafka.clients.admin.AdminClientConfig
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

    @Bean
    fun commandTransformer(competitionStateService: CompetitionStateService,
                           mapper: ObjectMapper,
                           competitionStateRepository: CompetitionStateRepository,
                           competitionStateResolver: CompetitionStateResolver,
                           clusterSession: ClusterSession) = ValueTransformerWithKeySupplier<String, Command, List<EventHolder>> {
        CompetitionCommandTransformer(competitionStateService,
                competitionStateRepository,
                competitionStateResolver,
                mapper,
                clusterSession)
    }

    @Bean
    fun snapshotEventsProcessor(clusterSession: ClusterSession) = ProcessorSupplier<String, EventHolder> {
        StateSnapshotForwardingProcessor(clusterSession, COMPETITION_STATE_SNAPSHOT_STORE_NAME)
    }

    @Bean
    fun streamProcessingTopology(
            eventProcessor: ProcessorSupplier<String, EventHolder>,
            commandTransformer: ValueTransformerWithKeySupplier<String, Command, List<EventHolder>>,
            mapper: ObjectMapper,
            adminUtils: KafkaAdminUtils,
            props: KafkaProperties, clusterSession: ClusterSession): StreamsBuilder {
        return CompetitionProcessingStreamsBuilderFactory(
                CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME,
                CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME,
                CompetitionServiceTopics.CATEGORY_COMMANDS_TOPIC_NAME,
                CompetitionServiceTopics.COMPETITION_INTERNAL_EVENTS_TOPIC_NAME,
                commandTransformer, eventProcessor, adminUtils, props, mapper).createBuilder()
    }

    @Bean
    fun streams(builder: StreamsBuilder, kafkaProperties: KafkaProperties): KafkaStreams {
        val streamProperties = Properties().apply { putAll(kafkaProperties.streamProperties) }
        return KafkaStreams(builder.build(), streamProperties)
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    fun competitionProcessStream(kafkaStreams: KafkaStreams, adminUtils: KafkaAdminUtils) = LeaderProcessStreams(adminUtils, kafkaStreams)
}