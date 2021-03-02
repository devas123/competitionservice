package compman.compsrv.kafka.streams.transformer

import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.RocksDBRepository
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.AcknowledgingConsumerAwareMessageListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import reactor.core.publisher.EmitterProcessor
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.*

@Component
@Profile("!offline")
class CommandListener(private val commandTransformerFactory: CommandExecutionServiceFactory,
                      private val kafkaTemplate: KafkaTemplate<String, EventDTO>,
                      private val rocksDBRepository: RocksDBRepository) : AcknowledgingConsumerAwareMessageListener<String, CommandDTO> {
    companion object {
        private val log = LoggerFactory.getLogger(CommandListener::class.java)
    }

    private val processor = EmitterProcessor.create<Pair<ConsumerRecord<String, CommandDTO>, Acknowledgment?>>()

    init {
        processor.publishOn(Schedulers.parallel()).concatMap { pair ->
            val m = pair.first
            Mono.fromCallable { commandTransformerFactory.getCompetitionCommandTransformer(m.key()).transform(m, rocksDBRepository, kafkaTemplate, eventsFilterPredicate) }
                    .map { Optional.ofNullable(pair.second) }
        }.subscribe({ it.ifPresent { ack -> ack.acknowledge() } }, { error -> log.error("Error while processing command.", error) }, {})
    }

    val eventsFilterPredicate = { it: EventDTO -> when (it.type) {
        EventType.ERROR_EVENT -> {
            log.warn("Error event: $it")
            false
        }
        EventType.DUMMY, EventType.INTERNAL_COMPETITION_INFO -> false
        else -> true
    }}

    override fun onMessage(m: ConsumerRecord<String, CommandDTO>, acknowledgment: Acknowledgment?, consumer: Consumer<*, *>?) {
        processor.onNext(m to acknowledgment)
    }
}