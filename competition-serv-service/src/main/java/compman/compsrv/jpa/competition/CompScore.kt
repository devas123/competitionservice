package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.CompScoreDTO
import javax.persistence.*


@Embeddable
@Access(AccessType.FIELD)
class CompScore(
        @ManyToOne(optional = false)
        @JoinColumn(name = "compscore_competitor_id", nullable = false, updatable = false)
        val competitor: Competitor,
        @Embedded
        val score: Score) {
    fun toDTO() = CompScoreDTO().setScore(score.toDTO()).setCompetitor(competitor.toDTO())
}