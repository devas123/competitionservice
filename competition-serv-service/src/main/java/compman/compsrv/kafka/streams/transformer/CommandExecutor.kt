package compman.compsrv.kafka.streams.transformer

import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.AcknowledgingConsumerAwareMessageListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.EmitterProcessor
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.*

@Component
class CommandExecutor(private val commandTransformerFactory: CompetitionCommandTransformerFactory,
                      private val kafkaTemplate: KafkaTemplate<String, EventDTO>,
                      transactionManager: PlatformTransactionManager) : AcknowledgingConsumerAwareMessageListener<String, CommandDTO> {
    companion object {
        private val log = LoggerFactory.getLogger("commandProcessingLog")
    }

    private val processor = EmitterProcessor.create<Pair<ConsumerRecord<String, CommandDTO>, Acknowledgment?>>()
    private val transactionTemplate = TransactionTemplate(transactionManager)

    init {
        processor.publishOn(Schedulers.parallel()).flatMap { pair ->
            val m = pair.first
            Mono.fromCallable { commandTransformerFactory.getCompetitionCommandTransformer(m.key()).transform(m, transactionTemplate, kafkaTemplate, eventsFilterPredicate) }.publishOn(Schedulers.boundedElastic())
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