package compman.compsrv.kafka.streams

import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.kafka.serde.*
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.competition.CompetitionDashboardState
import compman.compsrv.model.competition.CompetitionProperties
import compman.compsrv.model.competition.CompetitionStatus
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandScope
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.schedule.DashboardPeriod
import compman.compsrv.model.schedule.Schedule
import compman.compsrv.service.CompetitionPropertiesService
import compman.compsrv.service.DashboardStateService
import compman.compsrv.service.StateQueryService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.ValueTransformerSupplier
import org.apache.kafka.streams.state.QueryableStoreTypes
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore
import org.apache.kafka.streams.state.Stores
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LeaderProcessStreams(private val adminClient: KafkaAdminUtils,
                           private val competitionStateService: CompetitionPropertiesService,
                           private val dashboardStateService: DashboardStateService,
                           private val stateQueryService: StateQueryService, private val kafkaProperties: KafkaProperties) {

    companion object {
        private val log = LoggerFactory.getLogger(LeaderProcessStreams::class.java)
        const val COMPETITION_PROPERTIES_STORE_NAME = "competition_properties"
        const val COMPETITION_DASHBOARD_STATE_STORE_NAME = "competition_dashboard_state"
        const val ROUTING_METADATA_KEY = "routing"
        const val CORRELATION_ID_KEY = "correlationId"
        const val KEY_METADATA_KEY = "key"
    }

    private val mapper: ObjectMapper = ObjectMapperFactory.createObjectMapper()
    private val streamOfCompetitions: KafkaStreams
    private val scheduledTasksExecutor = Executors.newSingleThreadScheduledExecutor()
    private val internalCommandProducer: KafkaProducer<String, Command>

    val metadataService: MetadataService

    init {
        val streamProperties = Properties().apply { putAll(kafkaProperties.streamProperties) }

        adminClient.createTopicIfMissing(CompetitionServiceTopics.COMPETITION_STATE_CHANGELOG_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor, compacted = true)
        adminClient.createTopicIfMissing(CompetitionServiceTopics.DASHBOARD_STATE_CHANGELOG_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor, compacted = true)
        val categoriesCommandsTopic = adminClient.createTopicIfMissing(CompetitionServiceTopics.CATEGORY_COMMANDS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        val competitionsCommandsTopic = adminClient.createTopicIfMissing(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        val competitionsEventsTopic = adminClient.createTopicIfMissing(CompetitionServiceTopics.COMPETITION_EVENTS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        val competitionsInternalEventsTopic = adminClient.createTopicIfMissing(CompetitionServiceTopics.COMPETITION_INTERNAL_EVENTS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        val matsCommandsTopic = adminClient.createTopicIfMissing(CompetitionServiceTopics.MAT_COMMANDS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        val matsGlobalCommandsTopic = adminClient.createTopicIfMissing(CompetitionServiceTopics.DASHBOARD_COMMANDS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        val matsGlobalEventsTopic = adminClient.createTopicIfMissing(CompetitionServiceTopics.DASHBOARD_EVENTS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        val matsGlobalInternalEventsTopic = adminClient.createTopicIfMissing(CompetitionServiceTopics.MAT_GLOBAL_INTERNAL_EVENTS_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        val builder = createStreamsBuilder(competitionsCommandsTopic, competitionsEventsTopic, categoriesCommandsTopic, competitionsInternalEventsTopic)
        val topology = addMatsCommandsProcessing(builder, matsCommandsTopic, matsGlobalCommandsTopic, matsGlobalEventsTopic, matsGlobalInternalEventsTopic).build()
        streamOfCompetitions = KafkaStreams(topology, streamProperties)
        metadataService = MetadataService(streamOfCompetitions)
        Runtime.getRuntime().addShutdownHook(thread(start = false) { adminClient.close() })
        Runtime.getRuntime().addShutdownHook(thread(start = false) { streamOfCompetitions.close(10, TimeUnit.SECONDS) })

        val producerProperties = Properties().apply { putAll(kafkaProperties.producer.properties) }
        producerProperties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.canonicalName
        producerProperties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = CommandSerializer::class.java.canonicalName

        internalCommandProducer = KafkaProducer(producerProperties)

        scheduledTasksExecutor.scheduleWithFixedDelay(thread(start = false) {
            try {
                val catStore = getDashboardStateStore()
                catStore.all().use {
                    while (it.hasNext()) {
                        try {
                            val catStateKeyValue = it.next()
                            if (catStateKeyValue.value != null) {
                                val competitionId = catStateKeyValue.value.competitionId
                                internalCommandProducer.send(ProducerRecord(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, competitionId, Command(competitionId,
                                        CommandType.CHECK_DASHBOARD_OBSOLETE, null, null, emptyMap())))
                            }
                        } catch (e: Throwable) {
                            log.error("Exception while checking for dashboard obsolete", e)
                        }
                    }
                }
            } catch (e: Throwable) {
                log.error("Exception while checking for obsolete", e)
            }
        }, JobStream.OBSOLETE_CHECK_INTERVAL_MILLIS, JobStream.OBSOLETE_CHECK_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)

    }

    private fun createStreamsBuilder(competitionsCommandsTopic: String, competitionsEventsTopic: String, categoriesCommandsTopic: String, competitionsInternalEventsTopic: String): StreamsBuilder {
        val builder = StreamsBuilder()
        val propsStoreBuilder = Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(COMPETITION_PROPERTIES_STORE_NAME),
                Serdes.String(),
                Serdes.serdeFrom(CompetitionPropsSerializer(), CompetitionPropsDeserializer()))
        builder.addStateStore(propsStoreBuilder)
        val allCommands = builder.stream<String, Command>(competitionsCommandsTopic, Consumed.with(Serdes.String(), CommandSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))
                .filter { key, value -> value != null && !key.isNullOrBlank() }


        val competitionCommands = allCommands.filter { _, value ->
            value.type.scopes.contains(CommandScope.COMPETITION)
        }

        val categoryCommands = allCommands.filter { _, value ->
            value.type.scopes.contains(CommandScope.CATEGORY)
        }

        val matCommands = allCommands.filter { _, value ->
            value.type.scopes.contains(CommandScope.MAT)
        }

        categoryCommands.selectKey { _, value -> value.categoryId }.to(CompetitionServiceTopics.CATEGORY_COMMANDS_TOPIC_NAME)
        matCommands.selectKey { _, value -> value.matId }.to(CompetitionServiceTopics.MAT_COMMANDS_TOPIC_NAME)

        competitionCommands
                .transformValues(ValueTransformerSupplier {
                    GlobalCompetitionCommandExecutorTransformer(COMPETITION_PROPERTIES_STORE_NAME,
                            competitionStateService,
                            mapper)
                }, COMPETITION_PROPERTIES_STORE_NAME)
                .flatMapValues { value -> value.toList() }
                .filterNot { _, value -> value == null || value.type == EventType.DUMMY }
                .to({ _, event, _ -> KafkaAdminUtils.getEventRouting(event, competitionsInternalEventsTopic) }, Produced.with(Serdes.String(), EventSerde()))

        val internalEventsStream = builder.stream<String, EventHolder>(competitionsInternalEventsTopic, Consumed.with(Serdes.String(), EventSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))

        internalEventsStream.filterNot { _, value -> value.type.name.startsWith("internal", ignoreCase = true) }.to(competitionsEventsTopic, Produced.with(Serdes.String(), EventSerde()))

        internalEventsStream
                .peek { key, value ->
                    if (listOf(EventType.COMPETITION_DELETED, EventType.COMPETITION_CREATED, EventType.COMPETITION_STARTED).contains(value.type)) {
                        log.info("Received an event about change in competitions' number: $key = $value")
                    }
                    if (value.type != EventType.ERROR_EVENT) {
                        log.info("Competition properties changed: $key -> $value")
                    } else {
                        log.info("Error event: $key -> $value")
                    }
                }
                .flatMapValues { value ->
                    when (value.type) {
                        EventType.INTERNAL_ALL_BRACKETS_DROPPED -> {
                            try {
                                if (value.payload != null && value.payload?.containsKey("categories") == true) {
                                    val categories = mapper.convertValue(value.payload?.get("categories"), Array<String>::class.java)
                                    categories.map { Command(value.competitionId, CommandType.DROP_CATEGORY_BRACKETS_COMMAND, it, emptyMap()) }
                                } else {
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                log.error("Error while processing INTERNAL_ALL_BRACKETS_DROPPED event $value", e)
                                listOf(Command(value.competitionId, CommandType.DUMMY_COMMAND, null, mapOf("error" to e)))
                            }
                        }
                        EventType.INTERNAL_COMPETITOR_CATEGORY_CHANGED -> {
                            try {
                                val competitor = mapper.convertValue(value.payload?.get("fighter"), Competitor::class.java)
                                val newCategory = mapper.convertValue(value.payload?.get("newCategory"), CategoryDTO::class.java)
                                listOf(
                                        Command(value.competitionId, CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND, competitor.category.categoryId, value.payload),
                                        Command(value.competitionId, CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND, newCategory.categoryId, value.payload))
                            } catch (e: Exception) {
                                log.error("Error while processing INTERNAL_COMPETITOR_CATEGORY_CHANGED event $value", e)
                                listOf(Command(value.competitionId, CommandType.DUMMY_COMMAND, null, mapOf("error" to e)))
                            }
                        }
                        EventType.CATEGORY_ADDED -> listOf(Command(value.competitionId, CommandType.INIT_CATEGORY_STATE_COMMAND, value.categoryId, value.payload))
                        EventType.CATEGORY_DELETED -> listOf(Command(value.competitionId, CommandType.DELETE_CATEGORY_STATE_COMMAND, value.categoryId, value.payload))
                        EventType.SCHEDULE_GENERATED -> {
                            try {
                                val schedule = mapper.convertValue(value.payload?.get("schedule"), Schedule::class.java)
                                if (schedule != null) {
                                    schedule.periods?.flatMap { it.schedule.map { scheduleEntry -> scheduleEntry to it.fightsByMats } }?.map { pair ->
                                        Command(value.competitionId, CommandType.UPDATE_CATEGORY_FIGHTS_COMMAND, pair.first.categoryId, mapOf("fightsByMats" to pair.second))
                                    }
                                } else {
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                log.error("Error while processing SCHEDULE_GENERATED event $value", e)
                                listOf(Command(value.competitionId, CommandType.DUMMY_COMMAND, null, mapOf("error" to e)))
                            }
                        }
                        EventType.COMPETITION_DELETED -> {
                            val categories = value.payload?.get("categories") as? List<*>
                            (categories?.map { Command(value.competitionId, CommandType.DELETE_CATEGORY_STATE_COMMAND, it.toString(), emptyMap()) }
                                    ?: emptyList()) + Command(value.competitionId, CommandType.DELETE_DASHBOARD_STATE_COMMAND, null, null, emptyMap())
                                    .setMetadata(mapOf(ROUTING_METADATA_KEY to CompetitionServiceTopics.DASHBOARD_COMMANDS_TOPIC_NAME))
                        }
                        else -> listOf(Command(value.competitionId, CommandType.DUMMY_COMMAND, value.categoryId, emptyMap()))
                    }
                }
                .filterNot { _, value -> value.type == CommandType.DUMMY_COMMAND }
                .selectKey { _, value -> getCommandKey(value, value.categoryId.toString()) }
                .to({ _, value, _ -> KafkaAdminUtils.getCommandRouting(value, categoriesCommandsTopic) }, Produced.with(Serdes.String(), CommandSerde()))

        return builder
    }

    private fun addMatsCommandsProcessing(builder: StreamsBuilder, matsCommandsTopic: String, matsGlobalCommandsTopic: String, matsGlobalEventsTopic: String, matsGlobalInternalEventsTopic: String): StreamsBuilder {
        val dashboardStateStoreBuilder = Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(COMPETITION_DASHBOARD_STATE_STORE_NAME),
                Serdes.String(),
                Serdes.serdeFrom(CompetitionDashboardStateSerializer(), CompetitionDashboardStateDeserializer()))
        builder.addStateStore(dashboardStateStoreBuilder)
        val matsGlobalCommands = builder.stream<String, Command>(matsGlobalCommandsTopic, Consumed.with(Serdes.String(), CommandSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))



        matsGlobalCommands
                .filter { key, value -> value != null && !key.isNullOrBlank() }
                .transformValues(ValueTransformerSupplier {
                    MatGlobalCommandExecutorTransformer(COMPETITION_DASHBOARD_STATE_STORE_NAME, dashboardStateService)
                }, COMPETITION_DASHBOARD_STATE_STORE_NAME)
                .flatMapValues { value -> value.toList() }
                .filterNot { _, value -> value == null || value.type == EventType.DUMMY }
                .to({ _, value, _ ->
                    when {
                        value.metadata?.containsKey(ROUTING_METADATA_KEY) == true -> value.metadata?.get(ROUTING_METADATA_KEY).toString()
                        value.type.name.startsWith("internal", ignoreCase = true) -> matsGlobalInternalEventsTopic
                        else -> matsGlobalEventsTopic
                    }
                }, Produced.with(Serdes.String(), EventSerde()))

        val internalEventsStream = builder.stream<String, EventHolder>(matsGlobalInternalEventsTopic, Consumed.with(Serdes.String(), EventSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))

        internalEventsStream
                .peek { key, value ->
                    if (value.type != EventType.ERROR_EVENT) {
                        log.info("Competition dashboard properties changed: $key -> $value")
                    } else {
                        log.info("Error event: $key -> $value")
                    }
                }
                .flatMapValues { event ->
                    when (event.type) {
                        EventType.DASHBOARD_PERIOD_DELETED -> {
                            val period = mapper.convertValue(event.payload?.get("period"), DashboardPeriod::class.java)
                            period?.matIds?.map { matId -> Command(event.competitionId, CommandType.DELETE_MAT_STATE_COMMAND, event.categoryId, matId, emptyMap()) }
                                    ?: emptyList()
                        }
                        EventType.MAT_DELETED -> {
                            listOf(Command(event.competitionId, CommandType.DELETE_MAT_STATE_COMMAND, event.categoryId, event.matId, event.payload))
                        }
                        EventType.DASHBOARD_PERIOD_INITIALIZED -> {
                            val period = mapper.convertValue(event.payload?.get("period"), DashboardPeriod::class.java)
                            val props = stateQueryService.getCompetitionProperties(event.competitionId)!!
                            val schedulePeriod = props.schedule?.periods?.find { it.id == period.id }
                            val periodCategories = schedulePeriod?.schedule?.map { scheduleEntry -> scheduleEntry.categoryId }
                            val fights = periodCategories?.mapNotNull { stateQueryService.getCategoryState(it) }?.flatMap { categoryState ->
                                categoryState.brackets?.fights?.filter { !Schedule.obsoleteFight(it, categoryState.competitors.size == 3) }?.toList()
                                        ?: emptyList()
                            }
                            if (fights?.isEmpty() == false) {
                                val undispatchedFights = fights.filter { it.matId.isNullOrBlank() }
                                val commands = period.matIds.mapIndexed { _, matId ->
                                    val matFights = fights.filter { it.matId == matId }
                                    Command(event.competitionId, CommandType.INIT_MAT_STATE_COMMAND, event.categoryId, matId, mapOf("matFights" to matFights, "periodId" to period.id))
                                }
                                if (undispatchedFights.isNotEmpty()) {
                                    //we have undispatched fights (with no mat)
                                    log.warn("Undispatched fights: $undispatchedFights")
                                    commands +
                                            Command(event.competitionId,
                                                    CommandType.ADD_UNDISPATCHED_MAT_COMMAND,
                                                    event.categoryId,
                                                    mapOf("periodId" to period.id, "matFights" to undispatchedFights))
                                                    .setMetadata(mapOf(ROUTING_METADATA_KEY to CompetitionServiceTopics.DASHBOARD_COMMANDS_TOPIC_NAME))
                                } else {
                                    commands
                                }
                            } else {
                                emptyList()
                            }

                        }
                        EventType.UNDISPATCHED_MAT_ADDED -> {
                            listOf(Command(event.competitionId, CommandType.INIT_MAT_STATE_COMMAND, event.categoryId, event.matId, event.payload))
                        }
                        else -> listOf(Command(event.competitionId, CommandType.DUMMY_COMMAND, event.categoryId, event.matId, emptyMap()))
                    }
                }
                .filterNot { _, value -> value.type == CommandType.DUMMY_COMMAND }
                .selectKey { _, value -> getCommandKey(value, value?.matId.toString()) }
                .to({ _, value, _ -> KafkaAdminUtils.getCommandRouting(value, matsCommandsTopic) }, Produced.with(Serdes.String(), CommandSerde()))

        return builder
    }

    private fun getCommandKey(value: Command?, defaultKey: String) = when {
        value?.metadata?.containsKey(KEY_METADATA_KEY) == true -> value.metadata?.get(KEY_METADATA_KEY).toString()
        value?.metadata?.containsKey(ROUTING_METADATA_KEY) == true -> {
            val routing = value.metadata?.get(ROUTING_METADATA_KEY)
            when (routing) {
                CompetitionServiceTopics.DASHBOARD_COMMANDS_TOPIC_NAME -> value.competitionId
                CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME -> value.competitionId
                else -> defaultKey
            }
        }
        else -> defaultKey
    }


    fun readCompProperties(statuses: Array<CompetitionStatus>?): Array<CompetitionProperties> {
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
        scheduledTasksExecutor.shutdown()
        scheduledTasksExecutor.awaitTermination(10, TimeUnit.SECONDS)
        internalCommandProducer.close()
    }

    fun getCompetitionProperties(competitionId: String): CompetitionProperties? {
        try {
            val store = getCompetitionPropertiesStore()
            return store?.get(competitionId)
        } catch (e: Exception) {
            log.error("Exception while getting properties for competition with id $competitionId", e)
            log.info("Metadata for store $COMPETITION_PROPERTIES_STORE_NAME: ${metadataService.streamsMetadataForStore(COMPETITION_PROPERTIES_STORE_NAME)}")
            throw e
        }
    }

    fun getDashboardState(competitionId: String): CompetitionDashboardState? {
        try {
            val store = getDashboardStateStore()
            return store.get(competitionId)
        } catch (e: Exception) {
            log.error("Exception while getting properties for competition with id $competitionId", e)
            log.info("Metadata for store $COMPETITION_DASHBOARD_STATE_STORE_NAME: ${metadataService.streamsMetadataForStore(COMPETITION_DASHBOARD_STATE_STORE_NAME)}")
            throw e
        }
    }

    private fun getCompetitionPropertiesStore(): ReadOnlyKeyValueStore<String, CompetitionProperties>? =
            streamOfCompetitions.store(COMPETITION_PROPERTIES_STORE_NAME, QueryableStoreTypes.keyValueStore<String, CompetitionProperties>())

    private fun getDashboardStateStore() =
            streamOfCompetitions.store(COMPETITION_DASHBOARD_STATE_STORE_NAME, QueryableStoreTypes.keyValueStore<String, CompetitionDashboardState>())

}