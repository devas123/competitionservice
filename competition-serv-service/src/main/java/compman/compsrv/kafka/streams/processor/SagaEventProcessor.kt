package compman.compsrv.kafka.streams.processor

import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.service.saga.Saga
import compman.compsrv.service.saga.SagaManager
import org.apache.kafka.streams.kstream.ValueTransformer
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore

class SagaEventProcessor(private val sagaStoreName: String, private val sagaManager: SagaManager) : ValueTransformer<EventHolder, List<Command>> {

    private lateinit var context: ProcessorContext

    private lateinit var sagaStore: KeyValueStore<String, String>

    override fun init(context: ProcessorContext) {
        this.context = context
        sagaStore = context.getStateStore(sagaStoreName) as KeyValueStore<String, String>
    }


    override fun transform(value: EventHolder?): List<Command> {
        if (value == null) {
            return emptyList()
        }
        return try {
            val saga = getSaga(value)
            val sagaKey = getSagaKey(value)
            if (saga?.completed() == false) {
                saga.acceptEvent(value)
                val newSagaState = saga.getState()
                try {
                    if (!newSagaState.completed()) {
                        sagaStore.put(sagaKey, sagaManager.serializeSagaState(newSagaState, getSagaType(value)))
                    } else {
                        sagaStore.delete(sagaKey)
                    }
                    newSagaState.nextStep()
                } catch (e: Exception) {
                    newSagaState.compensatingActions()
                }
            } else {
                sagaStore.delete(sagaKey)
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getSagaKey(value: EventHolder): String {
        return value.metadata?.get("saga_key").toString()
    }

    private fun getSagaType(value: EventHolder): String {
        return value.metadata?.get("saga_type").toString()
    }

    private fun getSaga(value: EventHolder?): Saga? {
        return if (value != null) {
            val sagaData = sagaStore.get(getSagaKey(value))
            val sagaType = getSagaType(value)
            sagaManager.getSaga(sagaData, sagaType, value.competitionId, value.correlationId)
        } else {
            null
        }
    }


    override fun close() {
        sagaStore.close()
    }

}