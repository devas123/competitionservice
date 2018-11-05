package compman.compsrv.kafka.streams

import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.service.ICommandProcessingService
import org.apache.kafka.streams.kstream.ValueTransformer
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

abstract class StateForwardingValueTransformer<StateType>(private val stateStoreName: String, private val stateForawrdingTopic: String, private val commandProcessingService: ICommandProcessingService<StateType, Command, EventHolder>) : ValueTransformer<Command, List<EventHolder>> {


    private val log = LoggerFactory.getLogger(this.javaClass)


    private lateinit var stateStore: KeyValueStore<String, StateType>
    protected lateinit var context: ProcessorContext
    private val updatesCounter = AtomicInteger(0)

    override fun init(context: ProcessorContext?) {
        this.context = context ?: throw IllegalStateException("Context cannot be null")
        stateStore = (context.getStateStore(stateStoreName)
                ?: throw IllegalStateException("Cannot get stateStore store $stateStoreName")) as KeyValueStore<String, StateType>
    }

    private fun getState(competitionId: String): StateType? {
        if (stateStore[competitionId] == null) {
            log.warn("There are no properties for competition $competitionId")
        }
        return stateStore[competitionId]
    }

    override fun transform(command: Command): List<EventHolder>? {
        return try {
            val stateKey = getStateKey(command)
            val currentState = getState(stateKey)
            val validationErrors = canExecuteCommand(currentState, command)
            if (validationErrors.isEmpty()) {
                val eventsToApply = commandProcessingService.process(command, currentState)
                val (newState, eventsToSend) = commandProcessingService.batchApply(eventsToApply, currentState)
                if (eventsToSend.isNotEmpty() && !eventsToSend.any { it.type == EventType.ERROR_EVENT }) {
                    if (newState != null) {
                        stateStore.put(stateKey, newState)
                    } else {
                        stateStore.delete(stateKey)
                    }
                }
                eventsToSend
            } else {
                log.error("Command not valid: ${validationErrors.joinToString(separator = ",")}")
                emptyList()
            }
        } catch(e: Throwable) {
            log.error("Exception: ", e)
            emptyList()
        }
    }

    override fun close() {
        try {
            stateStore.close()
        } catch (e: Exception) {
            log.warn("Exception while closing store." +
                    "", e)
        }
    }
    abstract fun getStateKey(command: Command?): String

    open fun canExecuteCommand(state: StateType?, command: Command?): List<String> = emptyList()
}