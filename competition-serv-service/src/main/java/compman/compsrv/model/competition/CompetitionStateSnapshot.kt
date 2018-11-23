package compman.compsrv.model.competition

import javax.persistence.*


@Entity
class CompetitionStateSnapshot(
        @Id @GeneratedValue(strategy = GenerationType.AUTO) val id: Long? = null,
        val competitionId: String,
        val eventPartition: Int,
        val eventOffset: Long,
        @Column(columnDefinition = "BINARY(32) NOT NULL")
        val state: ByteArray)