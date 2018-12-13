package compman.compsrv.kafka.streams.processor

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.model.competition.CompetitionState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.service.ICommandProcessingService
import org.apache.kafka.streams.kstream.ValueTransformerWithKey
import org.apache.kafka.streams.processor.ProcessorContext
import org.slf4j.LoggerFactory
import java.util.*

abstract class AbstractCommandTransformer(
        private val commandProcessingService: ICommandProcessingService<CompetitionState, Command, EventHolder>,
        private val clusterSession: ClusterSession,
        private val mapper: ObjectMapper) : ValueTransformerWithKey<String, Command, List<EventHolder>> {


    private val log = LoggerFactory.getLogger(this.javaClass)


    private lateinit var context: ProcessorContext

    override fun init(context: ProcessorContext?) {
        this.context = context ?: throw IllegalStateException("Context cannot be null")
    }

    abstract fun getState(id: String): Optional<CompetitionState>
    abstract fun saveState(readOnlyKey: String, state: CompetitionState)
    abstract fun deleteState(id: String)

    override fun transform(readOnlyKey: String, command: Command): List<EventHolder>? {
        return try {
            val currentStateOpt = getState(readOnlyKey)
            currentStateOpt.map { currentState ->
                val validationErrors = canExecuteCommand(currentState, command)
                if (validationErrors.isEmpty()) {
                    val eventsToApply = commandProcessingService.process(command, currentState)
                    val (newState, eventsToSend) = commandProcessingService.batchApply(eventsToApply, currentState)
                    if (eventsToSend.isNotEmpty() && eventsToSend.any { it.type != EventType.ERROR_EVENT }) {
                        if (newState != null) {
                            saveState(readOnlyKey, newState)
                        } else {
                            deleteState(readOnlyKey)
                            clusterSession.broadcastCompetitionProcessingStopped(setOf(readOnlyKey))
                        }
                    }
                    if (context.offset() % 50 == 0L && newState != null) {
                        eventsToSend + EventHolder(command.correlationId, command.competitionId, command.categoryId, command.matId, EventType.INTERNAL_STATE_SNAPSHOT_CREATED,
                                mapper.writeValueAsBytes(newState))
                    } else {
                        eventsToSend
                    }
                } else {
                    log.error("Command not valid: ${validationErrors.joinToString(separator = ",")}")
                    emptyList()
                }
            }.orElse(emptyList())
        } catch (e: Throwable) {
            log.error("Exception: ", e)
            emptyList()
        }
    }

    open fun canExecuteCommand(state: CompetitionState?, command: Command?): List<String> = emptyList()
}