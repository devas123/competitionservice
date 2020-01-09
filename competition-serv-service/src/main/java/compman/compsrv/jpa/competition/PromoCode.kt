package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.PromoCodeDTO
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id

@Entity
class PromoCode(
        @Id @GeneratedValue var id: Long?,
        var coefficient: BigDecimal,
        var competitionId: String,
        var startAt: Instant,
        var expireAt: Instant) {

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
}