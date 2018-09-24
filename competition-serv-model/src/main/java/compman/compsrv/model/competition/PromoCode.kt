package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class PromoCode @PersistenceConstructor @JsonCreator
constructor(
        @Id @JsonProperty("id") val id: String,
        @JsonProperty("coefficient") val coefficient: BigDecimal,
        @JsonProperty("competitionId") val competitionId: String,
        @JsonProperty("startAt") val startAt: Date,
        @JsonProperty("expireAt") val expireAt: Date) {
    constructor(id: String, competitionId: String, coefficient: BigDecimal) : this(id = id,
            coefficient = coefficient,
            competitionId = competitionId,
            startAt = Date.from(Instant.parse("1990-12-03T10:15:30.00Z")),
            expireAt = Date.from(Instant.parse("2057-12-03T10:15:30.00Z")))
}