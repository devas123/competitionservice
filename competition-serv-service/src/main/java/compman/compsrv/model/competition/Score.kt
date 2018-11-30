package compman.compsrv.model.competition

import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
data class Score(val points: Int,
                 val advantages: Int,
                 val penalties: Int) {
    fun isEmpty() = points == 0 && advantages == 0 && penalties == 0

    constructor() : this(points = 0, advantages = 0, penalties = 0)
}