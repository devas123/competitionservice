package compman.compsrv.jpa.brackets

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.brackets.BracketDescriptorDTO
import compman.compsrv.model.dto.brackets.BracketType
import org.hibernate.annotations.Cascade
import java.util.*
import javax.persistence.Entity
import javax.persistence.JoinColumn
import javax.persistence.OneToMany
import javax.persistence.OrderColumn

@Entity
class BracketDescriptor(
        id: String,
        var competitionId: String,
        var bracketType: BracketType,
        @OrderColumn(name = "fight_order")
        @OneToMany(orphanRemoval = true)
        @Cascade(org.hibernate.annotations.CascadeType.ALL)
        @JoinColumn(name = "bracket_id")
        var fights: MutableList<FightDescription>?) : AbstractJpaPersistable<String>(id) {

    override fun toString(): String {
        return "BracketDescriptor(id='$id', competitionId='$competitionId', bracketType=$bracketType, fights=${fights?.joinToString("\n")})"
    }
}