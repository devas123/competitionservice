package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
import compman.compsrv.repository.EventRepository
import compman.compsrv.service.processor.command.ICommandProcessor
import compman.compsrv.service.processor.event.IEventProcessor
import compman.compsrv.util.IDGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(propagation = Propagation.REQUIRED)
class CompetitionStateService(
        private val eventRepository: EventRepository,
        private val eventProcessors: List<IEventProcessor<CompetitionState>>,
        private val commandProcessors: List<ICommandProcessor<CompetitionState>>,
        private val mapper: ObjectMapper) : ICommandProcessingService<CommandDTO, EventDTO, CompetitionState> {

    companion object {
        private val log = LoggerFactory.getLogger(CompetitionStateService::class.java)
    }

    override fun apply(state: CompetitionState, event: EventDTO, isBatch: Boolean): Pair<CompetitionState, List<EventDTO>> {
        fun createErrorEvent(error: String, failedOn: String? = null) =
                EventDTO()
                        .setCategoryId(event.categoryId)
                        .setCorrelationId(event.correlationId ?: "")
                        .setCompetitionId(event.competitionId)
                        .setMatId(event.matId)
                        .setType(EventType.ERROR_EVENT)
                        .setPayload(mapper.writeValueAsString(ErrorEventPayload(error, failedOn)))
        return try {
            val eventWithId = event.setId(event.id ?: IDGenerator.uid())
            if (isBatch || !duplicateCheck(event)) {
                eventProcessors.filter { it.affectedEvents().contains(event.type) }.flatMap { it.applyEvent(eventWithId) }
                eventRepository.save(eventWithId.toEntity())
                listOf(eventWithId)
            } else {
                listOf(createErrorEvent("Duplicate event: CorrelationId: ${eventWithId.correlationId}", eventWithId.id.toString()))
            }
        } catch (e: Exception) {
            log.error("Error while applying event.", e)
            listOf(createErrorEvent(e.localizedMessage))
        }
    }

    override fun process(command: CommandDTO): List<EventDTO> {

        fun createErrorEvent(error: String) = EventDTO()
                .setId(IDGenerator.uid())
                .setCategoryId(command.categoryId)
                .setCorrelationId(command.correlationId)
                .setCompetitionId(command.competitionId)
                .setMatId(command.matId)
                .setType(EventType.ERROR_EVENT)
                .setPayload(mapper.writeValueAsString(ErrorEventPayload(error, command.correlationId)))
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


}