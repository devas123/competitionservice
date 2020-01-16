package compman.compsrv.jpa.brackets

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.brackets.StageType
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import javax.persistence.*

@Entity
class StageDescriptor(
        id: String,
        var name: String,
        var competitionId: String,
        var bracketType: BracketType,
        var stageType: StageType?,
        var stageStatus: StageStatus?,
        var stageOrder: Long?,
        var waitForPrevious: Boolean?,
        @OneToMany(orphanRemoval = true)
        @Cascade(CascadeType.ALL)
        @JoinColumn(name = "stage_id")
        var pointsAssignments: MutableSet<PointsAssignmentDescriptor>?,
        @OneToOne(orphanRemoval = true)
        @Cascade(CascadeType.ALL)
        @PrimaryKeyJoinColumn
        var inputDescriptor: StageInputDescriptor?,
        @OneToOne(orphanRemoval = true)
        @Cascade(CascadeType.ALL)
        @PrimaryKeyJoinColumn
        var stageResultDescriptor: StageResultDescriptor?,
        @OrderColumn(name = "fight_order")
        @OneToMany(orphanRemoval = true)
        @Cascade(CascadeType.ALL)
        @JoinColumn(name = "stage_id")
        var fights: MutableList<FightDescription>?) : AbstractJpaPersistable<String>(id) {

    override fun toString(): String {
        return "BracketDescriptor(id='$id', competitionId='$competitionId', bracketType=$bracketType, fights=${fights?.joinToString("\n")})"
    }
}