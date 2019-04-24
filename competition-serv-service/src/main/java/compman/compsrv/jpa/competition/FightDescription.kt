package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightStage
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
                       @ElementCollection
                       @CollectionTable(
                               name = "comp_scores",
                               joinColumns = [JoinColumn(name = "fight_id")]
                       )
                       var scores: Array<CompScore>,
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

    companion object {
        fun fromDTO(dto: FightDescriptionDTO) =
                FightDescription(
                        id = dto.id,
                        categoryId = dto.categoryId,
                        winFight = dto.winFight,
                        loseFight = dto.loseFight,
                        scores = dto.scores?.map { CompScore(Competitor.fromDTO(it.competitor), Score(it.score.points, it.score.advantages, it.score.penalties)) }?.toTypedArray() ?: emptyArray(),
                        parentId1 = dto.parentId1,
                        parentId2 = dto.parentId2,
                        duration = dto.duration,
                        round = dto.round,
                        stage = dto.stage,
                        fightResult = dto.fightResult?.let { FightResult.fromDTO(it) },
                        matId = dto.matId,
                        numberOnMat = dto.numberOnMat,
                        priority = dto.priority,
                        competitionId = dto.competitionId,
                        period = dto.period,
                        startTime = dto.startTime,
                        numberInRound = dto.numberInRound

                )
    }

    fun toDTO() = FightDescriptionDTO(
            id,
            categoryId,
            winFight,
            loseFight,
            scores.map { it.toDTO() }.toTypedArray(),
            parentId1,
            parentId2,
            duration,
            round,
            stage,
            fightResult?.toDTO(),
            matId,
            numberOnMat,
            priority,
            competitionId,
            period,
            startTime,
            numberInRound
    )

    constructor(fightId: String, categoryId: String, competitionId: String) : this(id = fightId,
            categoryId = categoryId,
            winFight = null,
            loseFight = null,
            scores = emptyArray(),
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
            scores = emptyArray(),
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
            scores = emptyArray(),
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
             scores: Array<CompScore> = this.scores,
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

    private fun canModifyFight() =
            stage == FightStage.PENDING && scores.all { it.score.isEmpty() }


    fun setCompetitorWithIndex(competitor: Competitor, index: Int): FightDescription {
        if (!canModifyFight()) {
            return this
        }
        if (scores.size > 2) {
            return this
        }
        val needToShift = scores.size == 2
        if (index == 0) {
            scores = if (needToShift) {
                arrayOf(CompScore(competitor, Score()), scores[1])
            } else {
                arrayOf(CompScore(competitor, Score()), scores[0])
            }
        } else if (index == 1) {
            scores = if (needToShift) {
                arrayOf(scores[0], CompScore(competitor, Score()))
            } else {
                arrayOf(CompScore(competitor, Score()))
            }
        }
        return this
    }

    fun pushCompetitor(competitor: Competitor): FightDescription {
        if (competitor.id == "fake") {
            return this
        }
        if (scores.size < 2) {
            scores += CompScore(competitor, Score())
        } else {
            throw RuntimeException("Fight is already packed. Cannot add competitors")
        }
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