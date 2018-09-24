package compman.compsrv.kafka.streams

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.cluster.ZookeeperSession
import compman.compsrv.kafka.serde.*
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.competition.CategoryState
import compman.compsrv.model.competition.MatState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.service.CategoryStateService
import compman.compsrv.validators.CategoryCommandsValidatorRegistry
import compman.compsrv.validators.MatCommandsValidatorRegistry
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.ValueTransformerSupplier
import org.apache.kafka.streams.state.HostInfo
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.Stores
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


class JobStream(kafkaProperties: KafkaProperties, competitionStateService: CategoryStateService, private val hostInfo: HostInfo,
                private val zookeeperSession: ZookeeperSession,
                private val categoryCommandsValidatorRegistry: CategoryCommandsValidatorRegistry,
                private val matCommandsValidatorRegistry: MatCommandsValidatorRegistry) {

    companion object {
        private val log = LoggerFactory.getLogger(JobStream::class.java)
        fun createApplicationId(baseApplicationId: String) = "${baseApplicationId}_worker"
        const val CATEGORY_STATE_STORE_NAME = "categorystate_store"
        const val MAT_STATE_STORE_NAME = "mat_state_store"
        const val OBSOLETE_CHECK_INTERVAL_MILLIS = 10 * 60 * 1000L
    }

    private val applicationId = createApplicationId(kafkaProperties.streamProperties[StreamsConfig.APPLICATION_ID_CONFIG].toString())
    private val adminClient: KafkaAdminUtils
    private val topology: Topology
    private val streams: KafkaStreams
    private val scheduledTasksExecutor = Executors.newSingleThreadScheduledExecutor()
    private val internalCommandProducer: KafkaProducer<String, Command>
    private var dead = false
    private val hook = thread(false) { this@JobStream.stop() }
    val metadataService: MetadataService
        get() = MetadataService(streams)

    init {
        val adminProps = Properties()
        adminProps[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaProperties.bootstrapServers
        adminClient = KafkaAdminUtils(adminProps)

        adminClient.createTopicIfMissing(CompetitionServiceTopics.CATEGORIES_COMMANDS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        adminClient.createTopicIfMissing(CompetitionServiceTopics.CATEGORIES_EVENTS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        adminClient.createTopicIfMissing(CompetitionServiceTopics.MATS_COMMANDS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        adminClient.createTopicIfMissing(CompetitionServiceTopics.MATS_EVENTS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)

        val producerProperties = Properties().apply { putAll(kafkaProperties.producer.properties) }
        producerProperties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        producerProperties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = CommandSerializer::class.java.canonicalName

        internalCommandProducer = KafkaProducer(producerProperties)

        val streamProperties = Properties().apply { putAll(kafkaProperties.streamProperties) }
        streamProperties[StreamsConfig.APPLICATION_ID_CONFIG] = applicationId
        streamProperties[StreamsConfig.STATE_CLEANUP_DELAY_MS_CONFIG] = 1000 * 60 * 60 * 24 * 3
        streamProperties[StreamsConfig.APPLICATION_SERVER_CONFIG] = "${hostInfo.host()}:${hostInfo.port()}"

        val builder = StreamsBuilder()

        val categoryStateStoreBuilder = Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(CATEGORY_STATE_STORE_NAME),
                Serdes.String(),
                Serdes.serdeFrom(CategoryStateSerializer(), CategoryStateDeserializer()))
        val matStateStoreBuilder = Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(MAT_STATE_STORE_NAME),
                Serdes.String(),
                Serdes.serdeFrom(MatStateSerializer(), MatStateDeserializer()))
        builder.addStateStore(categoryStateStoreBuilder)
        builder.addStateStore(matStateStoreBuilder)
        val categoryCommands = builder.stream<String, Command>(CompetitionServiceTopics.CATEGORIES_COMMANDS_TOPIC_NAME, Consumed.with(Serdes.String(), CommandSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))
        val categoryEvents = categoryCommands
                .transformValues(ValueTransformerSupplier { CategoryCommandExecutorTransformer(CATEGORY_STATE_STORE_NAME, competitionStateService, zookeeperSession, categoryCommandsValidatorRegistry) }, CATEGORY_STATE_STORE_NAME)
        categoryEvents.filter { _, value -> value != null && !value.isEmpty() }
                .flatMapValues { value -> value.toList() }
                .peek { key, value -> log.info("Produced a category event: $key -> $value") }
                .to({ _, event, _ -> KafkaAdminUtils.getEventRouting(event, CompetitionServiceTopics.CATEGORIES_EVENTS_TOPIC_NAME) }, Produced.with(Serdes.String(), EventSerde()))

        val matCommands = builder.stream<String, Command>(CompetitionServiceTopics.MATS_COMMANDS_TOPIC_NAME, Consumed.with(Serdes.String(), CommandSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))
        val matEvents = matCommands
                .transformValues(ValueTransformerSupplier { MatCommandExecutorTransformer(MAT_STATE_STORE_NAME, matCommandsValidatorRegistry) }, MAT_STATE_STORE_NAME)
        matEvents.filter { _, value -> value != null && !value.isEmpty() }
                .flatMapValues { value -> value.toList() }
                .peek { key, value -> log.info("Produced a mat event: $key -> $value") }
                .to({ _, event, _ -> KafkaAdminUtils.getEventRouting(event, CompetitionServiceTopics.MATS_EVENTS_TOPIC_NAME) }, Produced.with(Serdes.String(), EventSerde()))

        topology = builder.build()
        streams = KafkaStreams(topology, streamProperties)

        Runtime.getRuntime().addShutdownHook(thread(start = false) { adminClient.close() })
        scheduledTasksExecutor.scheduleWithFixedDelay(thread(start = false) {
            try {
                val catStore = getCategoryStateStore()
                catStore.all().use {
                    while (it.hasNext()) {
                        try {
                            val catStateKeyValue = it.next()
                            if (catStateKeyValue.value != null) {
                                val competititonId = catStateKeyValue.value.category.competitionId
                                val categoryId = catStateKeyValue.key
                                internalCommandProducer.send(ProducerRecord(CompetitionServiceTopics.COMPETITIONS_COMMANDS_TOPIC_NAME, competititonId, Command(competititonId, CommandType.CHECK_CATEGORY_OBSOLETE, categoryId, null, emptyMap())))
                            }
                        } catch (e: Throwable) {
                            log.error("Exception while checking for category obsolete", e)
                        }
                    }
                }
                val matStore = getMatStateStore()
                matStore.all().use {
                    while (it.hasNext()) {
                        try {
                            val matStateKeyValue = it.next()
                            if (matStateKeyValue.value != null) {
                                val competititonId = matStateKeyValue.value.competitionId
                                val matId = matStateKeyValue.key
                                internalCommandProducer.send(ProducerRecord(CompetitionServiceTopics.MATS_GLOBAL_COMMANDS_TOPIC_NAME, competititonId, Command(competititonId, CommandType.CHECK_MAT_OBSOLETE, null, matId, emptyMap())))
                            }
                        } catch (e: Throwable) {
                            log.error("Exception while checking for mat obsolete", e)
                        }
                    }
                }
            } catch (e: Throwable) {
                log.error("Exception while checking for obsolete", e)
            }
        }, OBSOLETE_CHECK_INTERVAL_MILLIS, OBSOLETE_CHECK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun start() {
        if (!dead) {
            log.info("Starting command processing stream, applicationId = $applicationId")
            streams.setUncaughtExceptionHandler { t, e -> log.error("Uncaught exception in thread $t.", e) }
            streams.start()
            streams.localThreadsMetadata().forEach { data -> log.info("$data") }
            Runtime.getRuntime().addShutdownHook(hook)
        } else {
            log.error("Starting a dead job, please create a new one.")
        }
    }


    fun stop() {
        try {
            if (!dead) {
                log.info("Stopping command processing stream. Host info: $hostInfo")
                streams.close(10, TimeUnit.SECONDS)
                adminClient.close()
                scheduledTasksExecutor.shutdown()
                scheduledTasksExecutor.awaitTermination(10, TimeUnit.SECONDS)
                internalCommandProducer.close()
                dead = true
            }
        } catch (e: Exception) {
            log.warn("Exception while stopping job stream.", e)
            dead = true
        }
    }


    fun getCategoryState(categoryId: String): CategoryState? {
        val store = getCategoryStateStore()
        return store.get(categoryId)
    }

    fun getMatState(matId: String): MatState? {
        val store = getMatStateStore()
        return store.get(matId)
    }

    private fun getCategoryStateStore() = streams.store(CATEGORY_STATE_STORE_NAME, QueryableStoreTypes.keyValueStore<String, CategoryState>())
    private fun getMatStateStore() = streams.store(MAT_STATE_STORE_NAME, QueryableStoreTypes.keyValueStore<String, MatState>())
    override fun toString(): String {
        return "JobStream(stream=${streams.localThreadsMetadata()?.joinToString("\n") {
            it?.toString() ?: "null"
        }}, hostInfo=$hostInfo, applicationId=$applicationId)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JobStream

        if (hostInfo != other.hostInfo) return false
        if (applicationId != other.applicationId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hostInfo.hashCode()
        result = 31 * result + applicationId.hashCode()
        return result
    }


}