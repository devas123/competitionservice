package compman.compsrv.jpa.competition

import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
data class Score(var points: Int?,
                 var advantages: Int?,
                 var penalties: Int?) {

    constructor() : this(points = 0, advantages = 0, penalties = 0)
}