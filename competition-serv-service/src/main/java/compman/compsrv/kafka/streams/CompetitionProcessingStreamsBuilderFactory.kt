package compman.compsrv.kafka.streams


import com.compman.starter.properties.KafkaProperties
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.kafka.serde.CommandSerde
import compman.compsrv.kafka.serde.CompetitionPropsDeserializer
import compman.compsrv.kafka.serde.CompetitionPropsSerializer
import compman.compsrv.kafka.serde.EventSerde
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.kafka.utils.KafkaAdminUtils
import compman.compsrv.model.competition.Competitor
import compman.compsrv.model.dto.CategoryDTO
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandScope
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.schedule.Schedule
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.Topology
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Predicate
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.kstream.ValueTransformerSupplier
import org.apache.kafka.streams.state.Stores
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

class CompetitionProcessingStreamsBuilderFactory(
        private val competitionCommandsTopic: String,
        private val competitionEventsTopic: String,
        private val categoryCommandsTopic: String,
        private val competitionInternalEventsTopic: String,
        private val commandTransformer: ValueTransformerSupplier<Command, List<EventHolder>>,
        adminClient: KafkaAdminUtils,
        kafkaProperties: KafkaProperties,
        private val mapper: ObjectMapper) {

    companion object {
        private val log = LoggerFactory.getLogger("competitionProcessingStreams")
        const val COMPETITION_PROPERTIES_STORE_NAME = "competition_properties"
        const val COMPETITION_DASHBOARD_STATE_STORE_NAME = "competition_dashboard_state"
        const val ROUTING_METADATA_KEY = "routing"
        const val KEY_METADATA_KEY = "key"
    }



    init {
        adminClient.createTopicIfMissing(categoryCommandsTopic, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        adminClient.createTopicIfMissing(competitionCommandsTopic, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        adminClient.createTopicIfMissing(competitionEventsTopic, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        adminClient.createTopicIfMissing(competitionInternalEventsTopic, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor)
        adminClient.createTopicIfMissing(CompetitionServiceTopics.COMPETITION_STATE_CHANGELOG_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor, compacted = true)
        adminClient.createTopicIfMissing(CompetitionServiceTopics.DASHBOARD_STATE_CHANGELOG_TOPIC_NAME, kafkaProperties.defaultTopicOptions.partitions, kafkaProperties.defaultTopicOptions.replicationFactor, compacted = true)
        Runtime.getRuntime().addShutdownHook(thread(start = false) { adminClient.close() })
    }

    fun createBuilder()
            : StreamsBuilder {
        val builder = StreamsBuilder()
        val propsStoreBuilder = Stores.keyValueStoreBuilder(Stores.persistentKeyValueStore(COMPETITION_PROPERTIES_STORE_NAME),
                Serdes.String(),
                Serdes.serdeFrom(CompetitionPropsSerializer(), CompetitionPropsDeserializer()))
        builder.addStateStore(propsStoreBuilder)
        val allCommands = builder.stream<String, Command>(competitionCommandsTopic, Consumed.with(Serdes.String(), CommandSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))
                .filter { key, value -> value != null && !key.isNullOrBlank() }

        val partitions = allCommands.branch(

                Predicate { _, value ->
                    value.type.scopes.contains(CommandScope.COMPETITION)
                },
                Predicate { _, value ->
                    value.type.scopes.contains(CommandScope.CATEGORY)
                },
                Predicate { _, value ->
                    value.type.scopes.contains(CommandScope.MAT)
                }
        )

        val competitionCommands = partitions[0]
        val categoryCommands = partitions[1]
        val matCommands = partitions[2]

        categoryCommands.selectKey { _, value -> value.categoryId }.to(CompetitionServiceTopics.CATEGORY_COMMANDS_TOPIC_NAME)
        matCommands.selectKey { _, value -> value.matId }.to(CompetitionServiceTopics.MAT_COMMANDS_TOPIC_NAME)

        competitionCommands
                .transformValues(commandTransformer, COMPETITION_PROPERTIES_STORE_NAME)
                .flatMapValues { value -> value.toList() }
                .filterNot { _, value -> value == null || value.type == EventType.DUMMY }
                .to({ _, event, _ -> KafkaAdminUtils.getEventRouting(event, competitionInternalEventsTopic) }, Produced.with(Serdes.String(), EventSerde()))

        val internalEventsStream = builder.stream<String, EventHolder>(competitionInternalEventsTopic, Consumed.with(Serdes.String(), EventSerde()).withOffsetResetPolicy(Topology.AutoOffsetReset.EARLIEST))

        internalEventsStream.filterNot { _, value -> value.type.name.startsWith("internal", ignoreCase = true) }.to(competitionEventsTopic, Produced.with(Serdes.String(), EventSerde()))


        val internalEventBranches = internalEventsStream.branch(
                Predicate { _, value -> value.metadata?.get(ROUTING_METADATA_KEY) == "competition_saga" },
                Predicate { _, value -> value.metadata?.get(ROUTING_METADATA_KEY) == "dashboard_saga" },
                Predicate { _, value -> value.metadata?.get(ROUTING_METADATA_KEY) != "dashboard_saga" && value.metadata?.get(ROUTING_METADATA_KEY) != "competition_saga" })

//        internalEventBranches[0].transformValues(ValueTransformerSupplier { SagaEventProcessor(COMPETITION_SAGAS_STORE_NAME, sagaFactory) }).flatMapValues { values -> values }.to(CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME, Produced.with(Serdes.String(), CommandSerde()))
//        internalEventBranches[1].transformValues(ValueTransformerSupplier { SagaEventProcessor(DASHBOARD_SAGAS_STORE_NAME, sagaFactory) }).flatMapValues { values -> values }.to(CompetitionServiceTopics.DASHBOARD_COMMANDS_TOPIC_NAME, Produced.with(Serdes.String(), CommandSerde()))

        internalEventBranches[2]
                .flatMapValues { value ->
                    when (value.type) {
                        EventType.DROP_ALL_BRACKETS_SAGA_STARTED -> {
                            try {
                                if (value.payload != null && value.payload?.containsKey("categories") == true) {
                                    val categories = mapper.convertValue(value.payload?.get("categories"), Array<String>::class.java)
                                    categories.map { Command(value.competitionId, CommandType.DROP_CATEGORY_BRACKETS_COMMAND, it, emptyMap()) }
                                } else {
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                log.error("Error while processing DROP_ALL_BRACKETS_SAGA_STARTED event $value", e)
                                listOf(Command(value.competitionId, CommandType.DUMMY_COMMAND, null, mapOf("error" to e)))
                            }
                        }
                        EventType.INTERNAL_COMPETITOR_CATEGORY_CHANGED -> {
                            try {
                                val competitor = mapper.convertValue(value.payload?.get("fighter"), Competitor::class.java)
                                val newCategory = mapper.convertValue(value.payload?.get("newCategory"), CategoryDTO::class.java)
                                listOf(
                                        Command(value.competitionId, CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND, competitor.categoryId, value.payload),
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
                .to({ _, value, _ -> KafkaAdminUtils.getCommandRouting(value, categoryCommandsTopic) }, Produced.with(Serdes.String(), CommandSerde()))

        return builder
    }

    private fun getCommandKey(value: Command?, defaultKey: String) = when {
        value?.metadata?.containsKey(KEY_METADATA_KEY) == true -> value.metadata.get(KEY_METADATA_KEY).toString()
        value?.metadata?.containsKey(ROUTING_METADATA_KEY) == true -> {
            val routing = value.metadata.get(ROUTING_METADATA_KEY)
            when (routing) {
                CompetitionServiceTopics.DASHBOARD_COMMANDS_TOPIC_NAME -> value.competitionId
                CompetitionServiceTopics.COMPETITION_COMMANDS_TOPIC_NAME -> value.competitionId
                else -> defaultKey
            }
        }
        else -> defaultKey
    }

}