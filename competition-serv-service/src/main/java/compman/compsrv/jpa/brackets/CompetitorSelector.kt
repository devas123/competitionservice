package compman.compsrv.jpa.brackets

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.brackets.LogicalOperator
import compman.compsrv.model.dto.brackets.OperatorType
import compman.compsrv.model.dto.brackets.SelectorClassifier
import jdk.nashorn.internal.ir.annotations.Immutable
import javax.persistence.ElementCollection
import javax.persistence.Entity

@Entity
@Immutable
class CompetitorSelector(id: String?,
                         var applyToStageId: String?,
                         var logicalOperator: LogicalOperator,
                         var classifier: SelectorClassifier?,
                         val operator: OperatorType?,
                         @ElementCollection
                         var selectorValue: List<String>?) : AbstractJpaPersistable<String>(id)
