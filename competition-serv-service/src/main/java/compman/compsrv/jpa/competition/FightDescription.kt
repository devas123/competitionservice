package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.FightStage
import compman.compsrv.service.FightsGenerateService
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.*

@Entity
class FightDescription(id: String,
                       @Column(columnDefinition = "VARCHAR(255) REFERENCES category_descriptor(id)")
                       var categoryId: String,
                       var winFight: String?,
                       var fightName: String?,
                       var loseFight: String?,
                       @OrderColumn(name = "comp_score_order")
                       @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
                       @Cascade(CascadeType.ALL)
                       @JoinColumn(name = "comp_score_id")
                       var scores: MutableList<CompScore>?,
                       @Embedded
                       @AttributeOverrides(
                               AttributeOverride(name = "referenceType", column = Column(name = "parent_1_reference_type")),
                               AttributeOverride(name = "fightId", column = Column(name = "parent_1_fight_id"))
                       )
                       var parentId1: ParentFightReference?,
                       @Embedded
                       @AttributeOverrides(
                               AttributeOverride(name = "referenceType", column = Column(name = "parent_2_reference_type")),
                               AttributeOverride(name = "fightId", column = Column(name = "parent_2_fight_id"))
                       )
                       var parentId2: ParentFightReference?,
                       var duration: BigDecimal?,
                       var round: Int?,
                       var roundType: StageRoundType?,
                       var stage: FightStage?,
                       @Embedded
                       var fightResult: FightResult?,
                       var matId: String?,
                       var numberOnMat: Int?,
                       var priority: Int,
                       @Column(columnDefinition = "VARCHAR(255) REFERENCES competition_properties(id)")
                       var competitionId: String,
                       var period: String,
                       var startTime: Instant?,
                       var numberInRound: Int) : AbstractJpaPersistable<String>(id), Serializable {


    constructor(fightId: String, categoryId: String, competitionId: String) : this(id = fightId,
            categoryId = categoryId,
            winFight = null,
            loseFight = null,
            fightName = FightsGenerateService.ELIMINATION,
            scores = mutableListOf(),
            parentId1 = null,
            parentId2 = null,
            duration = null,
            roundType = null,
            round = null,
            stage = FightStage.PENDING,
            fightResult = null,
            matId = "",
            numberOnMat = 0,
            startTime = Instant.now(),
            priority = 0,
            period = "",
            competitionId = competitionId,
            numberInRound = 0)

    constructor(fightId: String, categoryId: String, round: Int, competitionId: String, numberInRound: Int) : this(id = fightId,
            categoryId = categoryId,
            winFight = null,
            loseFight = null,
            fightName = FightsGenerateService.ELIMINATION,
            scores = mutableListOf(),
            parentId1 = null,
            parentId2 = null,
            duration = null,
            roundType = StageRoundType.WINNER_BRACKETS,
            round = round,
            stage = FightStage.PENDING,
            fightResult = null,
            matId = "",
            numberOnMat = 0,
            priority = 0,
            period = "",
            competitionId = competitionId,
            numberInRound = numberInRound,
            startTime = Instant.now())

    constructor(fightId: String, categoryId: String, round: Int, winFight: String?, competitionId: String, numberInRound: Int) : this(id = fightId,
            categoryId = categoryId,
            winFight = winFight,
            loseFight = null,
            fightName = FightsGenerateService.ELIMINATION,
            scores = mutableListOf(),
            parentId1 = null,
            parentId2 = null,
            duration = null,
            round = round,
            roundType = StageRoundType.WINNER_BRACKETS,
            numberInRound = numberInRound,
            stage = FightStage.PENDING,
            fightResult = null,
            matId = "",
            numberOnMat = 0,
            priority = 0,
            period = "",
            competitionId = competitionId,
            startTime = Instant.now())


    fun copy(id: String = this.id!!,
             fightName: String? = this.fightName,
             categoryId: String = this.categoryId,
             winFight: String? = this.winFight,
             loseFight: String? = this.loseFight,
             scores: MutableList<CompScore>? = this.scores,
             parentId1: ParentFightReference? = this.parentId1,
             parentId2: ParentFightReference? = this.parentId2,
             duration: BigDecimal? = this.duration,
             roundType: StageRoundType? = this.roundType,
             round: Int? = this.round,
             stage: FightStage? = this.stage,
             fightResult: FightResult? = this.fightResult,
             matId: String? = this.matId,
             numberOnMat: Int? = this.numberOnMat,
             priority: Int = this.priority,
             competitionId: String = this.competitionId,
             period: String = this.period,
             startTime: Instant? = this.startTime,
             numberInRound: Int = this.numberInRound): FightDescription = FightDescription(id,
            categoryId, winFight, fightName, loseFight, scores, parentId1, parentId2,
            duration, round, roundType, stage, fightResult, matId, numberOnMat,
            priority, competitionId, period, startTime, numberInRound)


    fun pushCompetitor(competitor: Competitor): FightDescription {
        if (competitor.id == "fake") {
            return this
        }
        var localScores = scores
        if (localScores == null) {
            localScores = mutableListOf()
        }
        if (localScores.size < 2) {
            localScores.add(CompScore(competitor, Score()))
        } else {
            throw RuntimeException("Fight is already packed. Cannot add competitors")
        }
        return copy(scores = localScores)
    }

    fun setDuration(duration: BigDecimal): FightDescription {
        this.duration = duration
        return this
    }

    override fun toString(): String {
        return "FightDescription(competitors.size = ${scores?.size},winFight=$winFight, loseFight=$loseFight, parentId1=$parentId1, parentId2=$parentId2, duration=$duration, round=$round, roundType=$roundType, stage=$stage, matId=$matId, numberOnMat=$numberOnMat, priority=$priority, competitionId='$competitionId', period='$period', startTime=$startTime, numberInRound=$numberInRound)"
    }


}