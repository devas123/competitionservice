package compman.compsrv.kafka.streams.processor

import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.service.ICommandProcessingService
import org.apache.kafka.streams.kstream.ValueTransformer
import org.apache.kafka.streams.processor.ProcessorContext
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

abstract class StateForwardingCommandProcessor<StateType>(private val stateForawrdingTopic: String, private val commandProcessingService: ICommandProcessingService<StateType, Command, EventHolder>) : ValueTransformer<Command, List<EventHolder>> {


    private val log = LoggerFactory.getLogger(this.javaClass)


    protected lateinit var context: ProcessorContext
    private val updatesCounter = AtomicInteger(0)

    override fun init(context: ProcessorContext?) {
        this.context = context ?: throw IllegalStateException("Context cannot be null")
    }

    abstract fun getState(id: String): Optional<StateType>
    abstract fun saveState(state: StateType)
    abstract fun getStateKey(command: Command?): String
    abstract fun deleteState(id: String)

    override fun transform(command: Command): List<EventHolder>? {
        return try {
            val stateKey = getStateKey(command)
            val currentStateOpt = getState(stateKey)
            currentStateOpt.map {currentState ->
                val validationErrors = canExecuteCommand(currentState, command)
                if (validationErrors.isEmpty()) {
                    val eventsToApply = commandProcessingService.process(command, currentState)
                    val (newState, eventsToSend) = commandProcessingService.batchApply(eventsToApply, currentState)
                    if (eventsToSend.isNotEmpty() && !eventsToSend.any { it.type == EventType.ERROR_EVENT }) {
                        if (newState != null) {
                            saveState(newState)
                        } else {
                            deleteState(stateKey)
                        }
                    }
                    eventsToSend
                } else {
                    log.error("Command not valid: ${validationErrors.joinToString(separator = ",")}")
                    emptyList()
                }
            }.orElse(emptyList())
        } catch(e: Throwable) {
            log.error("Exception: ", e)
            emptyList()
        }
    }

    open fun canExecuteCommand(state: StateType?, command: Command?): List<String> = emptyList()
}