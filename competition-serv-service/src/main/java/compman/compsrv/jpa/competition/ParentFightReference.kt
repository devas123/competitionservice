package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.brackets.FightReferenceType
import javax.persistence.Embeddable

@Embeddable
class ParentFightReference(var referenceType: FightReferenceType?, var fightId: String?)
