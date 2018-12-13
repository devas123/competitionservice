package compman.compsrv.kafka.streams.processor

import compman.compsrv.model.competition.CompetitionStateSnapshot
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.repository.CommandCrudRepository
import compman.compsrv.repository.EventCrudRepository
import org.apache.kafka.streams.processor.Processor
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore

class StateSnapshotForwardingProcessor(private val stateSnapshotStoreName: String, private val commandCrudRepository: CommandCrudRepository, private val eventCrudRepository: EventCrudRepository) : Processor<String, EventHolder> {

    private lateinit var context : ProcessorContext
    private lateinit var stateStore: KeyValueStore<String, CompetitionStateSnapshot>

    override fun process(key: String?, value: EventHolder?) {
        if (key != null && value?.payload != null) {
            val newCompetitionStateSnapshot = CompetitionStateSnapshot(value.competitionId, context.partition(), context.offset(),
                    eventCrudRepository.findByCompetitionId(value.competitionId).map { it.map { onlyId -> onlyId.getId() }.toSet() }.orElse(emptySet()),
                    commandCrudRepository.findByCompetitionId(value.competitionId).map { it.map { onlyId -> onlyId.getId() }.toSet() }.orElse(emptySet()),
                    value.payload)
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