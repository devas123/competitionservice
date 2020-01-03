package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import java.util.*
import javax.persistence.Embedded
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne

@Entity
class CompScore(
        id: String,
        @ManyToOne(optional = false)
        @JoinColumn(name = "compscore_competitor_id", nullable = false, updatable = false)
        val competitor: Competitor,
        @Embedded
        val score: Score) : AbstractJpaPersistable<String>(id) {

    constructor(competitor: Competitor, score: Score) : this("${competitor.id}_${UUID.randomUUID()}", competitor, score)
}