package compman.compsrv.jpa.brackets

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.brackets.CompetitorResultType
import java.math.BigDecimal
import javax.persistence.Entity

@Entity
class PointsAssignmentDescriptor(id: String?,
                                 var classifier: CompetitorResultType?,
                                 var points: BigDecimal?,
                                 var additionalPoints: BigDecimal?): AbstractJpaPersistable<String>(id)