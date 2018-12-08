package compman.compsrv.kafka.utils

import compman.compsrv.kafka.streams.CompetitionProcessingStreamsBuilderFactory.Companion.ROUTING_METADATA_KEY
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.KafkaAdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.errors.TopicExistsException
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class KafkaAdminUtils(adminProps: Properties) {

    companion object {
        fun getCommandRouting(command: Command?, defaultTopic: String) = command?.metadata?.firstOrNull { it.key == ROUTING_METADATA_KEY }?.value ?: defaultTopic

        fun getEventRouting(eventHolder: EventHolder?, defaultTopic: String) = eventHolder?.findMetadataByKey(ROUTING_METADATA_KEY) ?: defaultTopic
    }

    private val kafkaAdminClient: AdminClient = KafkaAdminClient.create(adminProps)
    private val log = LoggerFactory.getLogger(KafkaAdminUtils::class.java)
    fun createTopicIfMissing(name: String, partitions: Int, replicationFactor: Short, compacted: Boolean = false): String {
        val topic = NewTopic(name, partitions, replicationFactor)
        if (compacted) {
            val topicProps = HashMap<String, String>()
            topicProps[TopicConfig.CLEANUP_POLICY_CONFIG] = TopicConfig.CLEANUP_POLICY_COMPACT
            topic.configs(topicProps)
        }

        try {
            val topicCreationResult = kafkaAdminClient.createTopics(listOf(topic))
            topicCreationResult.all().get(10, TimeUnit.SECONDS)

        } catch (e: ExecutionException) {
            if (e.cause?.javaClass == TopicExistsException::class.java) {
                log.info("Topic $name already exists, no need to create a new one.")
            } else {
                throw e
            }
        }
        return topic.name()
    }

    fun deleteTopics(vararg names: String) {
        kafkaAdminClient.deleteTopics(names.toList())
    }

    fun close() {
        try {
            kafkaAdminClient.close(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            log.warn("Exception when closing the admin client.", e)
        }
    }
}

