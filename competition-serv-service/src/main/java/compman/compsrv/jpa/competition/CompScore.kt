package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.util.IDGenerator
import java.util.*
import javax.persistence.*

@Entity
class CompScore(
        id: String,
        @ManyToOne(optional = false, fetch = FetchType.LAZY)
        @JoinColumn(name = "compscore_competitor_id", nullable = false, updatable = false)
        val competitor: Competitor,
        @Embedded
        val score: Score) : AbstractJpaPersistable<String>(id) {

    constructor(competitor: Competitor, score: Score) : this(IDGenerator.compScoreId(competitor.id!!), competitor, score)
}