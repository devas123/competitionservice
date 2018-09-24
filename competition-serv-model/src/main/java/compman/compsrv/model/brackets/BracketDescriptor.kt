package compman.compsrv.model.brackets

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import compman.compsrv.model.competition.FightDescription
import java.util.*

data class BracketDescriptor @JsonCreator constructor(@JsonProperty("bracketType") val bracketType: BracketType, @JsonProperty("fights") val fights: Array<FightDescription>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BracketDescriptor

        if (bracketType != other.bracketType) return false
        if (!Arrays.equals(fights, other.fights)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bracketType.hashCode()
        result = 31 * result + Arrays.hashCode(fights)
        return result
    }

    override fun toString(): String {
        return "BracketDescriptor(bracketType=$bracketType, fights=${fights.size})"
    }

    fun setFights(fights: Array<FightDescription>) = copy(fights = fights)
}