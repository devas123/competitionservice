package compman.compsrv.kafka.streams.processor

import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
import compman.compsrv.service.ICommandProcessingService
import org.apache.kafka.streams.kstream.ValueTransformerWithKey
import org.apache.kafka.streams.processor.ProcessorContext
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractCommandTransformer(
        private val commandProcessingService: ICommandProcessingService<CommandDTO, EventDTO>) : ValueTransformerWithKey<String, CommandDTO, List<EventDTO>> {


    private val log = LoggerFactory.getLogger(this.javaClass)


    private lateinit var context: ProcessorContext

    private val processedEventsNumber = ConcurrentHashMap<String, Long>()

    override fun init(context: ProcessorContext?) {
        this.context = context ?: throw IllegalStateException("Context cannot be null")
    }

    abstract fun initState(id: String)
    abstract fun getState(id: String): Optional<CompetitionState>

    override fun transform(readOnlyKey: String, command: CommandDTO): List<EventDTO>? {
        return try {
            log.info("Processing command: $command")
            initState(readOnlyKey)
            val validationErrors = canExecuteCommand(command)
            if (validationErrors.isEmpty()) {
                log.info("Command validated: $command")
                val eventsToApply = commandProcessingService.process(command)
                val eventsToSend = commandProcessingService.batchApply(eventsToApply)
                processedEventsNumber.compute(readOnlyKey) { _: String, u: Long? -> (u ?: 0) + 1 }
                if (processedEventsNumber.getOrDefault(readOnlyKey, 0) % 10 == 0L) {
                    getState(readOnlyKey).map { newState ->
                        eventsToSend + (EventDTO()
                                .setCategoryId(command.categoryId)
                                .setCorrelationId(command.correlationId)
                                .setCompetitionId(command.competitionId)
                                .setMatId(command.matId)
                                .setType(EventType.INTERNAL_STATE_SNAPSHOT_CREATED)
                                .setPayload(newState))
                    }.orElse(eventsToSend)
                } else {
                    eventsToSend
                }
            } else {
                log.error("Command not valid: ${validationErrors.joinToString(separator = ",")}")
                listOf(EventDTO()
                        .setCategoryId(command.categoryId)
                        .setCorrelationId(command.correlationId)
                        .setCompetitionId(command.competitionId)
                        .setMatId(command.matId)
                        .setType(EventType.ERROR_EVENT)
                        .setPayload(ErrorEventPayload(validationErrors.joinToString(separator = ","), command.correlationId)))
            }
        } catch (e: Throwable) {
            log.error("Exception: ", e)
            listOf(EventDTO()
                    .setCategoryId(command.categoryId)
                    .setCorrelationId(command.correlationId)
                    .setCompetitionId(command.competitionId)
                    .setMatId(command.matId)
                    .setType(EventType.ERROR_EVENT)
                    .setPayload(ErrorEventPayload(e.localizedMessage, command.correlationId)))
        }
    }

    open fun canExecuteCommand(command: CommandDTO?): List<String> = emptyList()
}