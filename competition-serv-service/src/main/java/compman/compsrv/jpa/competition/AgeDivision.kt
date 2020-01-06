package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.AgeDivisionDTO
import compman.compsrv.util.IDGenerator
import javax.persistence.Entity

@Entity
class AgeDivision(
        id: String,
        var name: String,
        var minimalAge: Int,
        var maximalAge: Int) : AbstractJpaPersistable<String>(id) {

    constructor(name: String, minimalAge: Int, maximalAge: Int) : this(IDGenerator.hashString("$name/$minimalAge/$maximalAge"), name, minimalAge, maximalAge)
}