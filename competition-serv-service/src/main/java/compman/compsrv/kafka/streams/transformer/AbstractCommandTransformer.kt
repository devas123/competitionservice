package compman.compsrv.kafka.streams.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.service.ICommandProcessingService
import compman.compsrv.util.createErrorEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory

abstract class AbstractCommandTransformer(
        private val executionService: ICommandProcessingService<CommandDTO, EventDTO>,
        private val mapper: ObjectMapper) {


    private val log = LoggerFactory.getLogger(this.javaClass)

    abstract fun initState(id: String, correlationId: String?)

    open fun transform(record: ConsumerRecord<String, CommandDTO>): List<EventDTO> {
        val command = record.value()
        val readOnlyKey = record.key()
        return commandExecutionLogic(command, readOnlyKey)
    }

    private fun createErrorEvent(command: CommandDTO, message: String?) = listOf(mapper.createErrorEvent(command, message))


    private fun commandExecutionLogic(command: CommandDTO, readOnlyKey: String): List<EventDTO> {
        return try {
            if (command.type != CommandType.CREATE_COMPETITION_COMMAND && command.type != CommandType.DELETE_COMPETITION_COMMAND) {
                initState(readOnlyKey, command.correlationId)
            }
            val validationErrors = canExecuteCommand(command)
            when {
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
        } catch (e: Throwable) {
            log.error("Exception while processing command: ", e)
            createErrorEvent(command, e.message)
        }
    }

    private tailrec fun eventLoop(appliedEvents: List<EventDTO>, eventsToApply: List<EventDTO>): List<EventDTO> {
        return when {
            eventsToApply.isEmpty() -> appliedEvents
            eventsToApply.any { it.type == EventType.ERROR_EVENT } -> {
                log.info("There were errors while processing the command. Returning error events.")
                appliedEvents + eventsToApply.filter { it.type == EventType.ERROR_EVENT }
            }
            else -> {
                log.info("Applying generated events: ${eventsToApply.joinToString("\n")}")
                val newEventsToApply = executionService.batchApply(eventsToApply)
                if (newEventsToApply.any { it.type == EventType.ERROR_EVENT }) {
                    log.info("There were errors while applying the events. Returning error events.")
                    appliedEvents + newEventsToApply.filter { it.type == EventType.ERROR_EVENT }
                } else {
                    eventLoop(appliedEvents + eventsToApply, newEventsToApply)
                }
            }
        }
    }

    open fun canExecuteCommand(command: CommandDTO?): List<String> = emptyList()
}