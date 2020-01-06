package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.WeightDTO
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.getId
import java.math.BigDecimal
import javax.persistence.Entity

@Entity
class Weight(id: String,
             var name: String,
             var maxValue: BigDecimal?,
             var minValue: BigDecimal?) : AbstractJpaPersistable<String>(id) {

    constructor(name: String,
                maxValue: BigDecimal?,
                minValue: BigDecimal?) : this(getId("$name/$maxValue/$minValue"), name, maxValue, minValue)

    constructor(name: String, maxvalue: BigDecimal) : this(name, maxvalue, BigDecimal.ZERO)
}