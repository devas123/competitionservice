package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.FightStage
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import java.io.Serializable
import java.time.Instant
import javax.persistence.*

@Entity
class FightDescription(id: String,
                       @Column(columnDefinition = "VARCHAR(255) REFERENCES category_descriptor(id)")
                       var categoryId: String,
                       var winFight: String?,
                       var loseFight: String?,
                       @OrderColumn(name = "comp_score_order")
                       @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
                       @Cascade(CascadeType.ALL)
                       @JoinColumn(name = "comp_score_id")
                       var scores: MutableList<CompScore>?,
                       var parentId1: String?,
                       var parentId2: String?,
                       var duration: Long?,
                       var round: Int?,
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
            scores = mutableListOf(),
            parentId1 = null,
            parentId2 = null,
            duration = null,
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
            scores = mutableListOf(),
            parentId1 = null,
            parentId2 = null,
            duration = null,
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
            scores = mutableListOf(),
            parentId1 = null,
            parentId2 = null,
            duration = null,
            round = round,
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
             categoryId: String = this.categoryId,
             winFight: String? = this.winFight,
             loseFight: String? = this.loseFight,
             scores: MutableList<CompScore>? = this.scores,
             parentId1: String? = this.parentId1,
             parentId2: String? = this.parentId2,
             duration: Long? = this.duration,
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
            categoryId, winFight, loseFight, scores, parentId1, parentId2,
            duration, round, stage, fightResult, matId, numberOnMat,
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
        scores = localScores
        return this
    }

    fun setDuration(duration: Long): FightDescription {
        this.duration = duration
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FightDescription

        if (id != other.id) return false
        if (categoryId != other.categoryId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + categoryId.hashCode()
        return result
    }
}