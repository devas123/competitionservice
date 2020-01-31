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
        @Column(columnDefinition = "VARCHAR(255) REFERENCES category_descriptor(id)")
        var categoryId: String,
        var bracketType: BracketType,
        var stageType: StageType?,
        var stageStatus: StageStatus?,
        var stageOrder: Int?,
        var waitForPrevious: Boolean?,
        var hasThirdPlaceFight: Boolean?,
        @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
        @Cascade(CascadeType.ALL)
        @JoinColumn(name = "stage_id")
        var pointsAssignments: MutableSet<PointsAssignmentDescriptor>?,
        @OneToOne(orphanRemoval = true, fetch = FetchType.LAZY)
        @Cascade(CascadeType.ALL)
        @PrimaryKeyJoinColumn
        var inputDescriptor: StageInputDescriptor?,
        @OneToOne(orphanRemoval = true, fetch = FetchType.LAZY)
        @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.DELETE, CascadeType.PERSIST)
        @PrimaryKeyJoinColumn
        var stageResultDescriptor: StageResultDescriptor?,
        @OrderColumn(name = "fight_order")
        @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
        @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.DELETE, CascadeType.PERSIST)
        @JoinColumn(name = "stage_id")
        var fights: MutableList<FightDescription>?) : AbstractJpaPersistable<String>(id) {

    override fun toString(): String {
        return "BracketDescriptor(id='$id', competitionId='$competitionId', bracketType=$bracketType, fights=${fights?.joinToString("\n")})"
    }
}