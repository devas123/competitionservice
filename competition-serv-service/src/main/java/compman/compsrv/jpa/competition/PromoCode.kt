package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.PromoCodeDTO
import java.math.BigDecimal
import java.time.ZonedDateTime
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
class PromoCode(
        @Id @GeneratedValue var id: Long?,
        var coefficient: BigDecimal,
        var competitionId: String,
        var startAt: ZonedDateTime,
        var expireAt: ZonedDateTime) {
    companion object {
        fun fromDTO(dto: PromoCodeDTO) = PromoCode(dto.id?.toLong(), dto.coefficient, dto.competitionId, dto.startAt, dto.expireAt)
    }

    override fun equals(other: Any?): Boolean {
        other ?: return false

        if (this === other) return true

        if (javaClass != other.javaClass) return false

        other as PromoCode

        return if (null == this.id) false else this.id == other.id
    }

    override fun hashCode(): Int {
        return 31
    }

    fun toDTO() = PromoCodeDTO()
            .setId(id.toString())
            .setCoefficient(coefficient)
            .setCompetitionId(competitionId)
            .setStartAt(startAt)
            .setExpireAt(expireAt)
}