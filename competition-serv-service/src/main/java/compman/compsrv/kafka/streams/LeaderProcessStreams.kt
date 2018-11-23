package compman.compsrv.kafka.streams

import com.compman.starter.properties.KafkaProperties
import compman.compsrv.kafka.serde.CommandSerializer
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.competition.CompetitionDashboardState
import compman.compsrv.model.competition.CompetitionState
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.es.commands.Command
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LeaderProcessStreams(private val adminClient: KafkaAdminUtils,
                           private val kafkaProperties: KafkaProperties,
                           builder: StreamsBuilder) {

    companion object {
        private val log = LoggerFactory.getLogger(LeaderProcessStreams::class.java)
    }

    private val streamOfCompetitions: KafkaStreams
    private val internalCommandProducer: KafkaProducer<String, Command>

    val metadataService: MetadataService

    init {
        val streamProperties = Properties().apply { putAll(kafkaProperties.streamProperties) }

        streamOfCompetitions = KafkaStreams(builder.build(), streamProperties)
        metadataService = MetadataService(streamOfCompetitions)

        Runtime.getRuntime().addShutdownHook(thread(start = false) { streamOfCompetitions.close(10, TimeUnit.SECONDS) })


        val producerProperties = Properties().apply { putAll(kafkaProperties.producer.properties) }
        producerProperties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        producerProperties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = CommandSerializer::class.java.canonicalName
        internalCommandProducer = KafkaProducer(producerProperties)
    }


    fun readCompProperties(statuses: Array<CompetitionStatus>?): Array<CompetitionState> {
        val store = getCompetitionPropertiesStore()
        return store?.let { keyValueStore ->
            keyValueStore.all().use {
                it.asSequence()
                        .map { kv ->
                            log.info("${kv.key} -> ${kv.value}")
                            kv.value
                        }
                        .filter { cp ->
                            statuses == null || statuses.contains(cp.status)
                        }.toList().toTypedArray()
            }
        } ?: emptyArray()
    }

    fun start() {
        log.info("Starting the leader process.")
        streamOfCompetitions.setUncaughtExceptionHandler { t, e -> log.error("Uncaught exception in thread $t.", e) }
        streamOfCompetitions.setStateListener { newState, oldState ->
            log.info("Streams old state = $oldState, new state = $newState")
            if (newState == KafkaStreams.State.ERROR) {
                log.error("Stream is in error state. Stopping leader process...")
                this.stop()
            }

            if (newState == KafkaStreams.State.NOT_RUNNING) {
                log.error("Stream is not running, stopping leader process...")
                this.stop()
            }
        }
        streamOfCompetitions.start()
        streamOfCompetitions.localThreadsMetadata().map { it.toString() }.forEach(log::info)
    }

    fun stop() {
        streamOfCompetitions.close(10, TimeUnit.SECONDS)
        adminClient.close()
        internalCommandProducer.close()
    }

    fun getCompetitionProperties(competitionId: String): CompetitionState? {
        try {
            val store = getCompetitionPropertiesStore()
            return store?.get(competitionId)
        } catch (e: Exception) {
            log.error("Exception while getting properties for competition with id $competitionId", e)
            log.info("Metadata for store ${CompetitionProcessingStreamsBuilderFactory.COMPETITION_PROPERTIES_STORE_NAME}: ${metadataService.streamsMetadataForStore(CompetitionProcessingStreamsBuilderFactory.COMPETITION_PROPERTIES_STORE_NAME)}")
            throw e
        }
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardState? {
        try {
            val store = getDashboardStateStore()
            return store.get(competitionId)
        } catch (e: Exception) {
            log.error("Exception while getting properties for competition with id $competitionId", e)
            log.info("Metadata for store ${CompetitionProcessingStreamsBuilderFactory.COMPETITION_DASHBOARD_STATE_STORE_NAME}: ${metadataService.streamsMetadataForStore(CompetitionProcessingStreamsBuilderFactory.COMPETITION_DASHBOARD_STATE_STORE_NAME)}")
            throw e
        }
    }

    private fun getCompetitionPropertiesStore(): ReadOnlyKeyValueStore<String, CompetitionState>? =
            streamOfCompetitions.store(CompetitionProcessingStreamsBuilderFactory.COMPETITION_PROPERTIES_STORE_NAME, QueryableStoreTypes.keyValueStore<String, CompetitionState>())

    private fun getDashboardStateStore() =
            streamOfCompetitions.store(CompetitionProcessingStreamsBuilderFactory.COMPETITION_DASHBOARD_STATE_STORE_NAME, QueryableStoreTypes.keyValueStore<String, CompetitionDashboardState>())

}