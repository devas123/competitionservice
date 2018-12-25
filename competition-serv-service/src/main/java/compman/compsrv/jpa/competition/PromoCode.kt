package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.PromoCodeDTO
import java.math.BigDecimal
import java.time.ZonedDateTime
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
data class PromoCode(
        @Id @GeneratedValue val id: Long?,
        val coefficient: BigDecimal,
        val competitionId: String,
        val startAt: ZonedDateTime,
        val expireAt: ZonedDateTime) {
    companion object {
        fun fromDTO(dto: PromoCodeDTO) = PromoCode(dto.id?.toLong(), dto.coefficient, dto.competitionId, dto.startAt, dto.expireAt)
    }
}