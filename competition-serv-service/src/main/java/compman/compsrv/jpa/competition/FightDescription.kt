package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightStage
import java.io.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.persistence.*

@Entity
data class FightDescription(
        @Id
        val id: String,
        val categoryId: String,
        val winFight: String?,
        val loseFight: String?,
        @OrderColumn(name = "competitor")
        @ElementCollection
        @CollectionTable(
                name = "comp_score",
                joinColumns = [JoinColumn(name = "fight_id")]
        )
        val scores: Array<CompScore>,
        val parentId1: String?,
        val parentId2: String?,
        val duration: Long?,
        val round: Int?,
        val stage: FightStage?,
        @Embedded
        val fightResult: FightResult?,
        val matId: String?,
        val numberOnMat: Int?,
        val priority: Int,
        val competitionId: String,
        val period: String,
        val startTime: ZonedDateTime?,
        val numberInRound: Int): Serializable {

    companion object {
        fun fromDTO(dto: FightDescriptionDTO) =
                FightDescription(
                        id = dto.id,
                        categoryId = dto.categoryId,
                        winFight = dto.winFight,
                        loseFight = dto.loseFight,
                        scores = dto.scores.map { CompScore(Competitor.fromDTO(it.competitor), Score(it.score.points, it.score.advantages, it.score.penalties)) }.toTypedArray(),
                        parentId1 = dto.parentId1,
                        parentId2 = dto.parentId2,
                        duration = dto.duration,
                        round = dto.round,
                        stage = dto.stage,
                        fightResult = FightResult.fromDTO(dto.fightResult),
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
            startTime = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()),
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
            startTime = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()))

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
            startTime = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault()))

    fun setStartTime(startTime: ZonedDateTime) = copy(startTime = startTime)
    fun setMat(mat: String?) = copy(matId = mat)
    fun setNumberOnMat(numberOnMat: Int?) = copy(numberOnMat = numberOnMat)

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
        return if (index == 0) {
            if (needToShift) {
                copy(scores = arrayOf(CompScore(competitor, Score()), scores[1]))
            } else {
                copy(scores = arrayOf(CompScore(competitor, Score()), scores[0]))
            }
        } else if (index == 1) {
            if (needToShift) {
                copy(scores = arrayOf(scores[0], CompScore(competitor, Score())))
            } else {
                copy(scores = scores + CompScore(competitor, Score()))
            }
        } else {
            this
        }
    }

    fun pushCompetitor(competitor: Competitor): FightDescription {
        if (competitor.id == "fake") {
            return this
        }
        if (scores.size < 2) {
            return copy(scores = scores + CompScore(competitor, Score()))
        } else {
            throw RuntimeException("Fight is already packed. Cannot add competitors")
        }
    }

    fun setDuration(duration: Long) = copy(duration = duration)

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