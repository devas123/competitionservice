package compman.compsrv.config

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.kafka.streams.CompetitionProcessingStreamsBuilderFactory
import compman.compsrv.kafka.streams.CompetitionProcessingStreamsBuilderFactory.Companion.COMPETITION_PROPERTIES_STORE_NAME
import compman.compsrv.kafka.streams.processor.CompetitionCommandProcessor
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.repository.CompetitionStateRepository
import compman.compsrv.service.CompetitionStateService
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.ValueTransformerSupplier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
open class KafkaStreamsConfiguration {

    @Bean
    open fun adminClient(props: KafkaProperties): KafkaAdminUtils {
        val adminProps = Properties()
        adminProps[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = props.bootstrapServers
        return KafkaAdminUtils(adminProps)
    }

    @Bean
    open fun commandTransformer(competitionStateService: CompetitionStateService, mapper: ObjectMapper, competitionStateRepository: CompetitionStateRepository) = ValueTransformerSupplier<Command, List<EventHolder>> {
        CompetitionCommandProcessor(COMPETITION_PROPERTIES_STORE_NAME,
                competitionStateService,
                competitionStateRepository,
                mapper)
    }

    @Bean
    open fun streamProcessingTopology(
            commandTransformer: ValueTransformerSupplier<Command, List<EventHolder>>,
            mapper: ObjectMapper,
            adminUtils: KafkaAdminUtils,
            props: KafkaProperties): StreamsBuilder {
        return CompetitionProcessingStreamsBuilderFactory(
                CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME,
                CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME,
                CompetitionServiceTopics.CATEGORY_COMMANDS_TOPIC_NAME,
                CompetitionServiceTopics.COMPETITION_INTERNAL_EVENTS_TOPIC_NAME,
                commandTransformer, adminUtils, props, mapper).createBuilder()
    }
}