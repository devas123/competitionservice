package compman.compsrv.service

import compman.compsrv.kafka.streams.transformer.CommandExecutionServiceFactory
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.RocksDBRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.util.*

@Component
class CommandDispatcher(private val rocksDBRepository: RocksDBRepository, private val commandTransformerFactory: CommandExecutionServiceFactory, private val kafkaTemplate: KafkaTemplate<String, EventDTO>) {
    private val processor = Sinks.many().multicast().onBackpressureBuffer<Pair<CommandDTO, Acknowledgment?>>()
    companion object {
        private val log = LoggerFactory.getLogger(CommandDispatcher::class.java)
    }

    init {
        processor.asFlux().publishOn(Schedulers.parallel()).concatMap { pair ->
            val m = pair.first
            Mono.fromCallable { commandTransformerFactory.getCompetitionCommandTransformer(m.competitionId).transform(m, rocksDBRepository, kafkaTemplate, eventsFilterPredicate) }
                .map { Optional.ofNullable(pair.second) }
        }.subscribe({ it.ifPresent { ack -> ack.acknowledge() } }, { error -> log.error("Error while processing command.", error) }, {})
    }

    val eventsFilterPredicate = { it: EventDTO -> when (it.type) {
        EventType.ERROR_EVENT -> {
            log.warn("Error event: $it")
            false
        }
        else -> true
    }}

    fun handleCommand(pair: Pair<CommandDTO, Acknowledgment?>) {
        processor.tryEmitNext(pair)
    }
}