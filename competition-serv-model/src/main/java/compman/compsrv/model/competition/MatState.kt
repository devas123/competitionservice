package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class MatState @JsonCreator constructor(@JsonProperty("correlationId") val correlationId: String,
                                             @JsonProperty("matId") val matId: String,
                                             @JsonProperty("periodId") val periodId: String,
                                             @JsonProperty("competitionId") val competitionId: String,
                                             @JsonProperty("fights") val fights: Array<FightDescription>) {
    constructor(correlationId: String, matId: String, periodId: String, competitionId: String) : this(correlationId, matId, periodId, competitionId, emptyArray())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MatState

        if (correlationId != other.correlationId) return false
        if (matId != other.matId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = correlationId.hashCode()
        result = 31 * result + matId.hashCode()
        return result
    }
    fun setFights(fights: Array<FightDescription>) = copy(fights = fights)
}