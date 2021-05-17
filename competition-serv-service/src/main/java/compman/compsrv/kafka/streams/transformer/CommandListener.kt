package compman.compsrv.kafka.streams.transformer

import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.service.CommandDispatcher
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.listener.AcknowledgingConsumerAwareMessageListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("!offline")
class CommandListener(val commandDispatcher: CommandDispatcher) : AcknowledgingConsumerAwareMessageListener<String, CommandDTO> {

    override fun onMessage(m: ConsumerRecord<String, CommandDTO>, acknowledgment: Acknowledgment?, consumer: Consumer<*, *>?) {
        commandDispatcher.handleCommand(m.value() to acknowledgment)
    }
}