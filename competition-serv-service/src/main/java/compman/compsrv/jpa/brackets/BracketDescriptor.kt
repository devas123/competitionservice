package compman.compsrv.jpa.brackets

import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.brackets.BracketDescriptorDTO
import compman.compsrv.model.dto.brackets.BracketType
import java.util.*
import javax.persistence.*

@Entity
data class BracketDescriptor(
        @Id val id: String,
        val competitionId: String,
        val bracketType: BracketType,
        @OrderColumn
        @OneToMany(orphanRemoval = true)
        @JoinColumn(name = "bracket_id")
        val fights: Array<FightDescription>) {

    companion object {
        fun fromDTO(dto: BracketDescriptorDTO) = BracketDescriptor(dto.id, dto.competitionId, dto.bracketType, dto.fights.map { FightDescription.fromDTO(it) }.toTypedArray())
    }

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
        return "BracketDescriptor(id='$id', competitionId='$competitionId', bracketType=$bracketType, fights=${Arrays.toString(fights)})"
    }

    fun toDTO(): BracketDescriptorDTO? {
        return BracketDescriptorDTO(id, competitionId, bracketType, fights.map { it.toDTO() }.toTypedArray())
    }
}