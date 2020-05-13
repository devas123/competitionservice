package compman.compsrv.kafka.streams.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.service.ICommandProcessingService
import compman.compsrv.service.processor.event.IEffects
import compman.compsrv.util.createErrorEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory

abstract class AbstractCommandTransformer(
        private val executionService: ICommandProcessingService<CommandDTO, EventDTO>,
        private val effects: IEffects,
        private val mapper: ObjectMapper) {


    private val log = LoggerFactory.getLogger(this.javaClass)

    abstract fun initState(id: String, correlationId: String?, transactional: Boolean)


    open fun transform(record: ConsumerRecord<String, CommandDTO>): List<EventDTO> {
        val command = record.value()
        val readOnlyKey = record.key()
        return runCatching { commandExecutionLogic(command, readOnlyKey) }.getOrElse { e ->
            log.error("Exception while processing command: ", e)
            createErrorEvent(command, e.message)
        }
    }

    private fun createErrorEvent(command: CommandDTO, message: String?) = listOf(mapper.createErrorEvent(command, message))


    private fun commandExecutionLogic(command: CommandDTO, readOnlyKey: String): List<EventDTO> {
        if (command.type != CommandType.CREATE_COMPETITION_COMMAND && command.type != CommandType.DELETE_COMPETITION_COMMAND) {
            initState(readOnlyKey, command.correlationId, command.type == CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND)
        }
        val validationErrors = canExecuteCommand(command)
        return when {
            validationErrors.isEmpty() -> {
                log.info("Command validated: $command")
                val eventsToApply = executionService.process(command)
                eventLoop(emptyList(), eventsToApply)
            }
            else -> {
                log.error("Command not valid: ${validationErrors.joinToString(separator = ",")}")
                createErrorEvent(command, validationErrors.joinToString(separator = ","))
            }
        }

    }

    private tailrec fun eventLoop(appliedEvents: List<EventDTO>, eventsToApply: List<EventDTO>): List<EventDTO> = when {
        eventsToApply.isEmpty() -> appliedEvents
        eventsToApply.any { it.type == EventType.ERROR_EVENT } -> {
            log.info("There were errors while processing the command. Returning error events.")
            appliedEvents + eventsToApply.filter { it.type == EventType.ERROR_EVENT }
        }
        else -> {
            log.info("Applying generated events: ${eventsToApply.joinToString("\n")}")
            executionService.batchApply(eventsToApply)
            val effects = eventsToApply.flatMap(effects::effects)
            if (effects.any { it.type == EventType.ERROR_EVENT }) {
                log.info("There were errors in the effects. Returning applied events.")
                appliedEvents + eventsToApply
            } else {
                eventLoop(appliedEvents + eventsToApply, effects)
            }
        }
    }

    open fun canExecuteCommand(command: CommandDTO?): List<String> = emptyList()
}