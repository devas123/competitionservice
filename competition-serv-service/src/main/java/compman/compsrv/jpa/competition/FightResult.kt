package compman.compsrv.jpa.competition

import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
class FightResult(var winnerId: String?,
                  var draw: Boolean?,
                  var reason: String?)