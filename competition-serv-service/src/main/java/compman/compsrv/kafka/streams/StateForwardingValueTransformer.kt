package compman.compsrv.kafka.streams

import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import org.apache.kafka.streams.kstream.ValueTransformer
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

abstract class StateForwardingValueTransformer<StateType>(private val stateStoreName: String, private val stateForawrdingTopic: String) : ValueTransformer<Command, List<EventHolder>> {


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
        fun createEvent(type: EventType, payload: Map<String, Any?>) = EventHolder(command.correlatioId!!, command.competitionId, command.categoryId
                ?: "null", command.matId, type, payload)
                .setMetadata(mapOf(LeaderProcessStreams.ROUTING_METADATA_KEY to stateForawrdingTopic, LeaderProcessStreams.CORRELATION_ID_KEY to command.correlatioId!!))
        try {
            val currentState = getState(getStateKey(command))
            val triple = doTransform(currentState, command)
            return if (triple.first != null && updatesCounter.getAndIncrement() % 10 == 0 && triple.third != null && triple.third?.any { it.type == EventType.ERROR_EVENT } == false) {
                if (triple.second != null) {
                    stateStore.put(command.competitionId, triple.second)
                } else {
                    stateStore.delete(command.competitionId)
                }
                triple.third!! + createEvent(EventType.INTERNAL_STATE_SNAPSHOT_CREATED, mapOf("state" to triple.second?.let { updateCorrelationId(it, command) }))
            } else {
                triple.third
            }
        } catch(e: Throwable) {
            log.error("Exception: ", e)
            return null
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
    abstract fun updateCorrelationId(currentState: StateType, command: Command): StateType

    abstract fun getStateKey(command: Command?): String
    abstract fun doTransform(currentState: StateType?, command: Command?): Triple<String?, StateType?, List<EventHolder>?>
}