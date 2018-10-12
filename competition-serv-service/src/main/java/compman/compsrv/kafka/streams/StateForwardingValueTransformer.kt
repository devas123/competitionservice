package compman.compsrv.kafka.streams

import compman.compsrv.cluster.StateChangelogForwarder
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.launch
import org.apache.kafka.streams.kstream.ValueTransformer
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore
import java.util.*

abstract class StateForwardingValueTransformer<ComType, EvType, StateType>(private val stateStoreName: String, changelogTopicname: String, producerProperties: Properties) : ValueTransformer<ComType, EvType> {


    private val forwarder = StateChangelogForwarder<String, StateType>(changelogTopicname, producerProperties)

    protected lateinit var stateStore: KeyValueStore<String, StateType>
    protected lateinit var context: ProcessorContext


    override fun init(context: ProcessorContext?) {
        this.context = context ?: throw IllegalStateException("Context cannot be null")
        stateStore = (context.getStateStore(stateStoreName)
                ?: throw IllegalStateException("Cannot get stateStore store $stateStoreName")) as KeyValueStore<String, StateType>

        stateStore.all().use {
            GlobalScope.launch(Dispatchers.Unconfined) {
                while (it.hasNext()) {
                    val kv = it.next()
                    forwarder.send(kv.key, kv.value)
                }
            }
        }
    }

    override fun transform(value: ComType?): EvType? {
        val triple = doTransform(value)
        if (triple.first != null) {
            GlobalScope.launch(Dispatchers.Unconfined) { forwarder.send(triple.first!!, triple.second) }
        }
        return triple.third
    }

    abstract fun doTransform(command: ComType?): Triple<String?, StateType?, EvType?>
}