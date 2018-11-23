package compman.compsrv.service

import compman.compsrv.model.competition.CompetitionStateSnapshot
import compman.compsrv.repository.CompetitionStateSnapshotCrudRepository
import io.scalecube.cluster.Cluster
import io.scalecube.transport.Message
import org.springframework.stereotype.Component

@Component
class CompetitionStateSnapshotService(private val competitionStateSnapshotCrudRepository: CompetitionStateSnapshotCrudRepository, private val cluster: Cluster) {

    companion object {
        const val TYPE_KEY = "type"
        const val COMPETITION_STATE_SNAPSHOT = "COMPETITION_STATE_SNAPSHOT"
    }

    init {
        cluster.listenGossips().subscribe { t: Message? ->
            if (t != null && t.header(TYPE_KEY) == COMPETITION_STATE_SNAPSHOT && t.data<Any?>() != null) {
                competitionStateSnapshotCrudRepository.save(t.data())
            }
        }
    }

    fun saveSnapshot(snapshot: CompetitionStateSnapshot) {
        competitionStateSnapshotCrudRepository.save(snapshot)
        cluster.spreadGossip(Message.withData(snapshot).header(TYPE_KEY, COMPETITION_STATE_SNAPSHOT).build()).subscribe()
    }

    fun getSnapshot(competitionId: String) = competitionStateSnapshotCrudRepository.findById(competitionId)
}