package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.AgeDivisionDTO
import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class AgeDivision(
        @Id val id: String,
        val minimalAge: Int,
        val maximalAge: Int) {

    companion object {
        fun fromDTO(dto: AgeDivisionDTO) = AgeDivision(dto.id, dto.minimalAge, dto.maximalAge)
    }

    fun toDTO(): AgeDivisionDTO? {
        return AgeDivisionDTO(id, minimalAge, maximalAge)
    }

    constructor(name: String, minimalAge: Int) : this(name, minimalAge, Int.MAX_VALUE)
}