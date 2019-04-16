package compman.compsrv.jpa.brackets

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.brackets.BracketDescriptorDTO
import compman.compsrv.model.dto.brackets.BracketType
import java.util.*
import javax.persistence.*

@Entity
class BracketDescriptor(
        id: String,
        var competitionId: String,
        var bracketType: BracketType,
        @OrderColumn
        @OneToMany(orphanRemoval = true, cascade = [CascadeType.ALL])
        @JoinColumn(name = "bracket_id")
        var fights: Array<FightDescription>) : AbstractJpaPersistable<String>(id) {

    companion object {
        fun fromDTO(dto: BracketDescriptorDTO) = BracketDescriptor(dto.id, dto.competitionId, dto.bracketType, dto.fights.map { FightDescription.fromDTO(it) }.toTypedArray())
    }

    override fun toString(): String {
        return "BracketDescriptor(id='$id', competitionId='$competitionId', bracketType=$bracketType, fights=${Arrays.toString(fights)})"
    }

    fun toDTO(): BracketDescriptorDTO? {
        return BracketDescriptorDTO(id, competitionId, bracketType, fights.map { it.toDTO() }.toTypedArray())
    }
}