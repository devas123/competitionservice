package compman.compsrv.kafka.streams.processor

import compman.compsrv.cluster.ClusterSession
import compman.compsrv.model.competition.CompetitionStateSnapshot
import compman.compsrv.model.es.events.EventHolder
import org.apache.kafka.streams.processor.Processor
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore

class StateSnapshotForwardingProcessor(private val clusterSession: ClusterSession, private val stateSnapshotStoreName: String) : Processor<String, EventHolder> {

    private lateinit var context : ProcessorContext
    private lateinit var stateStore: KeyValueStore<String, CompetitionStateSnapshot>

    override fun process(key: String?, value: EventHolder?) {
        if (key != null && value?.payload != null) {
            val newCompetitionStateSnapshot = CompetitionStateSnapshot(null, value.competitionId, context.partition(), context.offset(), value.payload)
            clusterSession.broadcastCompetitionStateSnapshot(newCompetitionStateSnapshot)
            stateStore.put(value.competitionId, newCompetitionStateSnapshot)
        }
    }

    override fun init(context: ProcessorContext?) {
        this.context = context ?: throw IllegalStateException("context is null")
        stateStore = this.context.getStateStore(stateSnapshotStoreName) as KeyValueStore<String, CompetitionStateSnapshot>
    }

    override fun close() {
    }
}