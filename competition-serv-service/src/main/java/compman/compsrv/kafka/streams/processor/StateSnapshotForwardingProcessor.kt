package compman.compsrv.kafka.streams.processor

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.cluster.ClusterSession
import compman.compsrv.model.dto.competition.CompetitionStateSnapshot
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.CommandCrudRepository
import compman.compsrv.repository.EventCrudRepository
import org.apache.kafka.streams.processor.Processor
import org.apache.kafka.streams.processor.ProcessorContext
import org.apache.kafka.streams.state.KeyValueStore

class StateSnapshotForwardingProcessor(private val stateSnapshotStoreName: String, private val commandCrudRepository: CommandCrudRepository,
                                       private val eventCrudRepository: EventCrudRepository, private val clusterSession: ClusterSession,
                                       private val mapper: ObjectMapper) : Processor<String, EventDTO> {

    private lateinit var context: ProcessorContext
    private lateinit var stateStore: KeyValueStore<String, CompetitionStateSnapshot>

    override fun process(key: String?, value: EventDTO?) {
        if (key != null && value?.payload != null && value.type == EventType.INTERNAL_STATE_SNAPSHOT_CREATED) {
            val newCompetitionStateSnapshot = CompetitionStateSnapshot(value.competitionId, clusterSession.localMemberId(), context.partition(), context.offset(),
                    eventCrudRepository.findByCompetitionId(value.competitionId).map { it.map { onlyId -> onlyId.getId() }.toSet() }.orElse(emptySet()),
                    commandCrudRepository.findByCompetitionId(value.competitionId).map { it.map { onlyId -> onlyId.getId() }.toSet() }.orElse(emptySet()),
                    mapper.writeValueAsString(value.payload))
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