package compman.compsrv.model.es.events.payload

import compman.compsrv.model.brackets.BracketType
import compman.compsrv.model.competition.FightDescription

data class BracketsGeneratedPayload(val fights: Array<FightDescription>, val bracketType: BracketType) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BracketsGeneratedPayload

        if (!fights.contentEquals(other.fights)) return false
        if (bracketType != other.bracketType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fights.contentHashCode()
        result = 31 * result + bracketType.hashCode()
        return result
    }
}