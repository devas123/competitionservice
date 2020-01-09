package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.repository.EventRepository
import compman.compsrv.service.processor.command.ICommandProcessor
import compman.compsrv.service.processor.event.IEventProcessor
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.createErrorEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import javax.persistence.EntityManager

@Component
@Transactional(propagation = Propagation.REQUIRED)
class CompetitionStateService(
        private val eventRepository: EventRepository,
        private val eventProcessors: List<IEventProcessor>,
        private val commandProcessors: List<ICommandProcessor>,
        private val entityManager: EntityManager,
        private val mapper: ObjectMapper) : ICommandProcessingService<CommandDTO, EventDTO> {

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionStateService::class.java)
    }

    override fun apply(event: EventDTO, isBatch: Boolean): List<EventDTO> {
        fun createErrorEvent(error: String) = mapper.createErrorEvent(event, error)
        return try {
            val eventWithId = event.setId(event.id ?: IDGenerator.uid())
            if (isBatch || !duplicateCheck(event)) {
                eventProcessors.filter { it.affectedEvents().contains(event.type) }.flatMap { it.applyEvent(eventWithId) }
                listOf(eventWithId)
            } else {
                listOf(createErrorEvent("Duplicate event: CorrelationId: ${eventWithId.correlationId}"))
            }
        } catch (e: Exception) {
            log.error("Error while applying event.", e)
            listOf(createErrorEvent(e.localizedMessage))
        }
    }

    override fun process(command: CommandDTO): List<EventDTO> {

        fun createErrorEvent(error: String) = mapper.createErrorEvent(command, error)
        return kotlin.runCatching {
            when {
                command.competitionId.isNullOrBlank() -> {
                    log.error("Competition id is empty, command $command")
                    listOf(createErrorEvent("Competition ID is empty."))
                }
                eventRepository.existsByCorrelationId(command.correlationId) -> {
                    log.error("Duplicate command.")
                    listOf(createErrorEvent("Duplicate command."))
                }
                else -> {
                    commandProcessors.filter { it.affectedCommands().contains(command.type) }.flatMap { it.executeCommand(command) }
                }
            }
        }.recover {
            log.error("Error while applying event.", it)
            listOf(createErrorEvent(it.localizedMessage ?: it.message ?: ""))
        }.getOrDefault(emptyList())
    }

    override fun duplicateCheck(event: EventDTO): Boolean = event.id?.let { eventRepository.existsById(it) } == true
    override fun flush() {
        entityManager.flush()
    }
}