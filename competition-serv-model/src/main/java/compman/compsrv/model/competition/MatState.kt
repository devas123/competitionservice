package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.schedule.DashboardPeriod

data class MatState @JsonCreator constructor(@JsonProperty("eventOffset") val eventOffset: Long,
                                             @JsonProperty("eventPartition") val eventPartition: Int,
                                             @JsonProperty("matId") val matId: String,
                                             @JsonProperty("periodId") val periodId: String,
                                             @JsonProperty("competitionId") val competitionId: String,
                                             @JsonProperty("fights") val fights: Array<FightDescription>) {
    constructor(matId: String, periodId: String, competitionId: String) : this(0, 0, matId, periodId, competitionId, emptyArray())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MatState

        if (eventOffset != other.eventOffset) return false
        if (eventPartition != other.eventPartition) return false
        if (matId != other.matId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = eventOffset.hashCode()
        result = 31 * result + eventPartition
        result = 31 * result + matId.hashCode()
        return result
    }

    fun setEventOffset(eventOffset: Long) = copy(eventOffset = eventOffset)
    fun setEventPartition(eventPartition: Int) = copy(eventPartition = eventPartition)
    fun setFights(fights: Array<FightDescription>) = copy(fights = fights)
}