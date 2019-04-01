package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.es.commands.Command
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
import compman.compsrv.repository.CommandCrudRepository
import compman.compsrv.service.processor.command.ICommandProcessor
import compman.compsrv.service.processor.event.IEventProcessor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Component
class CompetitionStateService(private val commandCrudRepository: CommandCrudRepository,
                              private val transactionTemplate: TransactionTemplate,
                              private val eventProcessors: List<IEventProcessor>,
                              private val commandProcessors: List<ICommandProcessor>,
                              private val mapper: ObjectMapper) : ICommandProcessingService<CommandDTO, EventDTO> {

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionStateService::class.java)
    }

    override fun apply(event: EventDTO): List<EventDTO> {
        fun createErrorEvent(error: String) =
                EventDTO()
                        .setCategoryId(event.categoryId)
                        .setCorrelationId(event.correlationId ?: "")
                        .setCompetitionId(event.competitionId)
                        .setMatId(event.matId)
                        .setType(EventType.ERROR_EVENT)
                        .setPayload(mapper.writeValueAsString(ErrorEventPayload(error, null)))
        return try {
            transactionTemplate.execute {
                eventProcessors.filter { it.affectedEvents().contains(event.type) }.flatMap { it.applyEvent(event) }
            } ?: emptyList()
        } catch (e: Exception) {
            log.error("Error while applying event.", e)
            listOf(createErrorEvent(e.localizedMessage))
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    override fun process(command: CommandDTO): List<EventDTO> {

        fun createErrorEvent(error: String) = EventDTO()
                .setCategoryId(command.categoryId)
                .setCorrelationId(command.correlationId)
                .setCompetitionId(command.competitionId)
                .setMatId(command.matId)
                .setType(EventType.ERROR_EVENT)
                .setPayload(mapper.writeValueAsString(ErrorEventPayload(error, command.correlationId)))
        log.info("Executing command: $command")
        return try {
            commandCrudRepository.save(Command.fromDTO(command))
            if (command.competitionId.isNullOrBlank()) {
                log.error("Competition id is empty, command $command")
                return listOf(createErrorEvent("Competition ID is empty."))
            }
            commandProcessors.filter { it.affectedCommands().contains(command.type) }.flatMap { it.executeCommand(command) }
        } catch (e: Exception) {
            log.error("Error while applying event.", e)
            listOf(createErrorEvent(e.localizedMessage))
        }
    }

}