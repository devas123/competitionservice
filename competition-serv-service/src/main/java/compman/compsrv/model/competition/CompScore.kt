package compman.compsrv.model.competition

import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import javax.persistence.*


@Embeddable
@Access(AccessType.FIELD)
data class CompScore(
        @ManyToOne(optional = false)
        @JoinColumn(name = "compscore_competitor_id", nullable = false, updatable = false)
        @OnDelete(action = OnDeleteAction.CASCADE)
        val competitor: Competitor,
        @Embedded
        val score: Score)