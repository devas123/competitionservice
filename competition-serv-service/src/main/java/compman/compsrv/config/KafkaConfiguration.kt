package compman.compsrv.config

import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.kafka.serde.CommandDeserializer
import compman.compsrv.kafka.serde.CommandSerializer
import compman.compsrv.kafka.serde.EventSerializer
import compman.compsrv.kafka.streams.transformer.CommandExecutionServiceFactory
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.RocksDBRepository
import compman.compsrv.service.CommandSyncExecutor
import compman.compsrv.service.CompetitionStateService
import compman.compsrv.service.resolver.CompetitionStateResolver
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*
import org.springframework.kafka.listener.AcknowledgingConsumerAwareMessageListener
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DefaultAfterRollbackProcessor
import org.springframework.util.backoff.FixedBackOff
import java.util.*

@Configuration
class KafkaConfiguration {

    private val partitions = 2
    private val replication = 1.toShort()

    @Bean
    fun commandsTopic() = NewTopic(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, partitions, replication)

    @Bean
    fun leaderChangelogTopic(): NewTopic {
        val config = mutableMapOf<String, String>()
        config[TopicConfig.CLEANUP_POLICY_CONFIG] = TopicConfig.CLEANUP_POLICY_COMPACT
        return NewTopic(CompetitionServiceTopics.LEADER_CHANGELOG_TOPIC, partitions, replication).configs(config)
    }

    @Bean(name = ["kafkaAdmin"], initMethod = "initialize")
    fun admin(props: KafkaProperties): KafkaAdmin {
        return KafkaAdmin(props.buildAdminProperties())
    }

    @Bean
    fun eventsTopic() = NewTopic(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME, partitions, replication)

    @Bean
    fun commandProducerFactory(kafkaProps: KafkaProperties): ProducerFactory<String, CommandDTO> {
        val props = producerProps(kafkaProps, CommandSerializer::class.java)
        return DefaultKafkaProducerFactory(props, StringSerializer(), CommandSerializer())
    }

    private fun <T> producerProps(kafkaProps: KafkaProperties, serializer: Class<T>): MutableMap<String, Any> {
        val props: MutableMap<String, Any> = mutableMapOf()
        props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProps.bootstrapServers
        props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = serializer
        props[ProducerConfig.RETRIES_CONFIG] = 10
        props.putAll(kafkaProps.buildProducerProperties())
        return props
    }


    @Bean
    fun eventProducerFactory(kafkaProps: KafkaProperties): ProducerFactory<String, EventDTO> {
        val props = producerProps(kafkaProps, EventSerializer::class.java)
        return DefaultKafkaProducerFactory(props, StringSerializer(), EventSerializer())
    }


    @Bean
    fun commandKafkaTemplate(commandProducerFactory: ProducerFactory<String, CommandDTO>): KafkaTemplate<String, CommandDTO> {
        return KafkaTemplate(commandProducerFactory)
    }

    @Bean
    fun eventKafkaTemplate(eventProducerFactory: ProducerFactory<String, EventDTO>): KafkaTemplate<String, EventDTO> {
        return KafkaTemplate(eventProducerFactory)
    }

    @Bean
    fun consumerFactory(kafkaProps: KafkaProperties): DefaultKafkaConsumerFactory<String, CommandDTO> {
        return DefaultKafkaConsumerFactory(consumerConfigs(kafkaProps), StringDeserializer(), CommandDeserializer())
    }

    @Bean
    fun consumerConfigs(kafkaProps: KafkaProperties): Map<String, Any> {
        val props = HashMap<String, Any>()
        props[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProps.bootstrapServers
        props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = CommandDeserializer::class.java
        props.putAll(kafkaProps.buildConsumerProperties())
        return props
    }


    @Bean
    fun container(
        cf: ConsumerFactory<String, CommandDTO>,
        kafkaProps: KafkaProperties,
        commandExecutor: AcknowledgingConsumerAwareMessageListener<String, CommandDTO>
    ): ConcurrentMessageListenerContainer<String, CommandDTO> {
        val props = ContainerProperties(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME)
        val consumerProps = kafkaProps.buildConsumerProperties()
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = CommandDeserializer::class.java
        props.kafkaConsumerProperties = Properties().apply { putAll(consumerProps) }
        props.groupId = kafkaProps.consumer.groupId
        props.messageListener = commandExecutor
        return ConcurrentMessageListenerContainer(cf, props).apply {
            concurrency = Runtime.getRuntime().availableProcessors()
            this.setAfterRollbackProcessor(DefaultAfterRollbackProcessor(FixedBackOff(0L, 2L)))
        }
    }

    @Bean
    fun kafkaListenerContainerFactory(
        configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
        kafkaConsumerFactory: ConsumerFactory<String, CommandDTO>
    ): ConcurrentKafkaListenerContainerFactory<Any, Any> {
        val factory: ConcurrentKafkaListenerContainerFactory<Any, Any> = ConcurrentKafkaListenerContainerFactory()
        @Suppress("UNCHECKED_CAST")
        configurer.configure(factory, kafkaConsumerFactory as ConsumerFactory<Any, Any>)
        return factory
    }


    @Bean
    fun commandTransformer(
        competitionStateService: CompetitionStateService,
        clusterOperations: ClusterOperations,
        commandSyncExecutor: CommandSyncExecutor,
        rocksDBRepository: RocksDBRepository,
        competitionStateResolver: CompetitionStateResolver
    ) = CommandExecutionServiceFactory(
        competitionStateService,
        competitionStateResolver,
        clusterOperations,
        commandSyncExecutor,
        rocksDBRepository
    )
}