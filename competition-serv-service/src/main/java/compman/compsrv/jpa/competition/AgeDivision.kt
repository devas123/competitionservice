package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.AgeDivisionDTO
import javax.persistence.Entity

@Entity
class AgeDivision(
        id: String,
        var minimalAge: Int,
        var maximalAge: Int) : AbstractJpaPersistable<String>(id) {

    companion object {
        fun fromDTO(dto: AgeDivisionDTO) = AgeDivision(dto.id, dto.minimalAge, dto.maximalAge)
    }

    fun toDTO(): AgeDivisionDTO? {
        return AgeDivisionDTO(id, minimalAge, maximalAge)
    }
}