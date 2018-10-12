package compman.compsrv.cluster

import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.channels.actor
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class StateChangelogForwarder<K, V>(private val topic: String, producerProperties: Properties) {

    companion object {
        private val log = LoggerFactory.getLogger(StateChangelogForwarder::class.java);
    }

    private val producer: KafkaProducer<K, V> = KafkaProducer(producerProperties)

    private val actorChannel = GlobalScope.actor<Pair<K, V?>>(capacity = 100) {
        for (msg in channel) {
            try {
                producer.send(ProducerRecord(topic, msg.first, msg.second))
            } catch (t: Throwable) {
                log.warn("Error while sending a record to topic $topic:", t)
            }
        }
    }

    suspend fun send(key: K, value: V?) {
        actorChannel.send(key to value)
    }

    fun close() {
        producer.flush()
        producer.close()
        actorChannel.close()
    }
}