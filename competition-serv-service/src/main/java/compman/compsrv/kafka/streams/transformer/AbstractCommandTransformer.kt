package compman.compsrv.kafka.streams.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
import compman.compsrv.service.ICommandProcessingService
import compman.compsrv.util.IDGenerator
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

abstract class AbstractCommandTransformer(
        private val commandProcessingService: ICommandProcessingService<CommandDTO, EventDTO>,
        private val mapper: ObjectMapper) {


    private val log = LoggerFactory.getLogger(this.javaClass)

    abstract fun initState(id: String, flushDb: Boolean)
    abstract fun getState(id: String): Optional<CompetitionState>

    @Transactional(propagation = Propagation.REQUIRED)
    open fun transform(record: ConsumerRecord<String, CommandDTO>): List<EventDTO> {
        val command = record.value()
        val readOnlyKey = record.key()
        fun createErrorEvent(message: String?) = listOf(EventDTO()
                .setCategoryId(command.categoryId)
                .setCorrelationId(command.correlationId)
                .setCompetitionId(command.competitionId)
                .setMatId(command.matId)
                .setType(EventType.ERROR_EVENT)
                .setPayload(mapper.writeValueAsString(ErrorEventPayload(message, command.correlationId))))
        return try {
            if (command.type != CommandType.CREATE_COMPETITION_COMMAND) {
                initState(readOnlyKey, command.type == CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND)
            }
            val validationErrors = canExecuteCommand(command)
            when {
                validationErrors.isEmpty() -> {
                    log.info("Command validated: $command")
                    val eventsToApply = commandProcessingService.process(command)
                    commandProcessingService.batchApply(eventsToApply)
                }
                else -> {
                    log.error("Command not valid: ${validationErrors.joinToString(separator = ",")}")
                    createErrorEvent(validationErrors.joinToString(separator = ","))
                }
            }
        } catch (e: Throwable) {
            log.error("Exception while processing command: ", e)
            createErrorEvent(e.message)
        }
    }

    open fun canExecuteCommand(command: CommandDTO?): List<String> = emptyList()
}