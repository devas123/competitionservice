package compman.compsrv.kafka.streams.transformer

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.dto.competition.CompetitionStateSnapshot
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
import compman.compsrv.service.ICommandProcessingService
import org.apache.kafka.streams.kstream.ValueTransformerWithKey
import org.apache.kafka.streams.processor.ProcessorContext
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionTemplate
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractCommandTransformer(
        private val commandProcessingService: ICommandProcessingService<CommandDTO, EventDTO>,
        private val transactionTemplate: TransactionTemplate,
        private val clusterSession: ClusterSession,
        private val mapper: ObjectMapper) : ValueTransformerWithKey<String, CommandDTO, List<EventDTO>> {


    private val log = LoggerFactory.getLogger(this.javaClass)


    private lateinit var context: ProcessorContext

    private val processedEventsNumber = ConcurrentHashMap<String, Long>()

    override fun init(context: ProcessorContext?) {
        this.context = context ?: throw IllegalStateException("Context cannot be null")
    }

    abstract fun initState(id: String, timestamp: Long)
    abstract fun getState(id: String): Optional<CompetitionState>

    override fun transform(readOnlyKey: String, command: CommandDTO): List<EventDTO>? {
        return try {
            transactionTemplate.execute {
                log.info("Processing command: $command")
                initState(readOnlyKey, context.timestamp())
                val validationErrors = canExecuteCommand(command)
                when {
                    command.type == CommandType.SEND_PROCESSING_INFO_COMMAND -> {
                        clusterSession.broadcastCompetitionProcessingInfo(setOf(command.competitionId ?: readOnlyKey))
                        emptyList()
                    }
                    validationErrors.isEmpty() -> {
                        log.info("Command validated: $command")
                        val eventsToApply = commandProcessingService.process(command)
                        val eventsToSend = commandProcessingService.batchApply(eventsToApply)
                        processedEventsNumber.compute(readOnlyKey) { _: String, u: Long? -> (u ?: 0) + 1 }
                        if (processedEventsNumber.getOrDefault(readOnlyKey, 0) % 10 == 0L) {
                            getState(readOnlyKey).map { newState ->
                                CompetitionStateSnapshot(command.competitionId, clusterSession.localMemberId(), context.partition(), context.offset(),
                                        emptySet(), emptySet(),
                                        mapper.writeValueAsString(newState.toDTO(includeCompetitors = true, includeBrackets = true)))
                            }
                        }
                        eventsToSend
                    }
                    else -> {
                        log.error("Command not valid: ${validationErrors.joinToString(separator = ",")}")
                        listOf(EventDTO()
                                .setCategoryId(command.categoryId)
                                .setCorrelationId(command.correlationId)
                                .setCompetitionId(command.competitionId)
                                .setMatId(command.matId)
                                .setType(EventType.ERROR_EVENT)
                                .setPayload(mapper.writeValueAsString(ErrorEventPayload(validationErrors.joinToString(separator = ","), command.correlationId))))
                    }
                }
            }
        } catch (e: Throwable) {
            log.error("Exception while processing command: ", e)
            listOf(EventDTO()
                    .setCategoryId(command.categoryId)
                    .setCorrelationId(command.correlationId)
                    .setCompetitionId(command.competitionId)
                    .setMatId(command.matId)
                    .setType(EventType.ERROR_EVENT)
                    .setPayload(mapper.writeValueAsString(ErrorEventPayload(e.localizedMessage, command.correlationId))))
        }
    }

    open fun canExecuteCommand(command: CommandDTO?): List<String> = emptyList()
}