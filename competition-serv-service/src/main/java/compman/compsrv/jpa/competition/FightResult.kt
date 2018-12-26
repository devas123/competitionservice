package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.FightResultDTO
import javax.persistence.Access
import javax.persistence.AccessType
import javax.persistence.Embeddable

@Embeddable
@Access(AccessType.FIELD)
data class FightResult(val winnerId: String?,
                       val draw: Boolean?,
                       val reason: String?) {
    companion object {
        fun fromDTO(dto: FightResultDTO) =
                FightResult(winnerId = dto.winnerId, draw = dto.draw, reason = dto.winnerId)
    }

    fun toDTO() = FightResultDTO().setDraw(draw).setReason(reason).setWinnerId(winnerId)
}