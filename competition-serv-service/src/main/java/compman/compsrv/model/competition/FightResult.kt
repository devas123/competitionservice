package compman.compsrv.model.competition

import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
data class FightResult(val winnerId: String?,
                       val draw: Boolean?,
                       var reason: String?)