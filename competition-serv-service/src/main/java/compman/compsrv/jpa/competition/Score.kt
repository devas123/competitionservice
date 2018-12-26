package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.ScoreDTO
import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
data class Score(val points: Int,
                 val advantages: Int,
                 val penalties: Int) {
    fun isEmpty() = points == 0 && advantages == 0 && penalties == 0
    fun toDTO(): ScoreDTO? {
        return ScoreDTO(points, advantages, penalties)
    }

    constructor() : this(points = 0, advantages = 0, penalties = 0)
}