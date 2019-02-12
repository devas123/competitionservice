package compman.compsrv.kafka.streams

import compman.compsrv.kafka.utils.KafkaAdminUtils
import org.apache.kafka.streams.KafkaStreams
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LeaderProcessStreams(private val adminClient: KafkaAdminUtils,
                           private val streamOfCompetitions: KafkaStreams) {

    companion object {
        private val log = LoggerFactory.getLogger(LeaderProcessStreams::class.java)
    }

    init {
        Runtime.getRuntime().addShutdownHook(thread(start = false) { streamOfCompetitions.close(Duration.ofSeconds(10)) })
    }


    fun start() {
        log.info("Starting the leader process.")
        streamOfCompetitions.setUncaughtExceptionHandler { t, e ->
            log.error("Uncaught exception in thread $t.", e)
        }
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
        streamOfCompetitions.close(Duration.ofSeconds(10))
        adminClient.close()
    }
}