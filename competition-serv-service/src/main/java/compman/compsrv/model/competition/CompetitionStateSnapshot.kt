package compman.compsrv.model.competition

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id


@Entity
class CompetitionStateSnapshot(
        @Id @GeneratedValue val id: Long? = null,
        val competitionId: String,
        val eventPartition: Int,
        val eventOffset: Long,
        val state: ByteArray)