package compman.compsrv.model.brackets

import compman.compsrv.model.competition.FightDescription
import java.util.*
import javax.persistence.*

@Entity
data class BracketDescriptor(
        @Id val categoryId: String? = null,
        val competitionId: String,
        val bracketType: BracketType,
        @OrderColumn
        @OneToMany(orphanRemoval = true)
        @JoinColumn(name = "bracket_id")
        val fights: Array<FightDescription>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BracketDescriptor

        if (bracketType != other.bracketType) return false
        if (!Arrays.equals(fights, other.fights)) return false

        return true
    }

    override fun hashCode(): Int = 31

    override fun toString(): String {
        return "BracketDescriptor(bracketType=$bracketType, fights=${fights.size})"
    }

    fun setFights(fights: Array<FightDescription>) = copy(fights = fights)
}