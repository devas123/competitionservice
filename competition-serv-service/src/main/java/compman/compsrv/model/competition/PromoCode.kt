package compman.compsrv.model.competition

import javax.persistence.Id
import java.math.BigDecimal
import java.util.*
import javax.persistence.Entity
import javax.persistence.GeneratedValue

@Entity
data class PromoCode(
        @Id @GeneratedValue val id: Long,
        val coefficient: BigDecimal,
        val competitionId: String,
        val startAt: Date,
        val expireAt: Date)