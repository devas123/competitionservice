package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.brackets.CompetitorResultType
import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
class FightResult(var winnerId: String?,
                  var resultType: CompetitorResultType?,
                  var reason: String?)