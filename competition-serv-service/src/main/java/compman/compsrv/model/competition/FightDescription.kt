package compman.compsrv.model.competition

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
        val competitors: Array<CompScore>,
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
        val numberInRound: Int) {

    constructor(fightId: String, categoryId: String, competitionId: String) : this(id = fightId,
            categoryId = categoryId,
            winFight = null,
            loseFight = null,
            competitors = emptyArray(),
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
            competitors = emptyArray(),
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
            competitors = emptyArray(),
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
            stage == FightStage.PENDING && competitors.all { it.score.isEmpty() }


    fun setCompetitorWithIndex(competitor: Competitor, index: Int): FightDescription {
        if (!canModifyFight()) {
            return this
        }
        if (competitors.size > 2) {
            return this
        }
        val needToShift = competitors.size == 2
        return if (index == 0) {
            if (needToShift) {
                copy(competitors = arrayOf(CompScore(competitor, Score()), competitors[1]))
            } else {
                copy(competitors = arrayOf(CompScore(competitor, Score()), competitors[0]))
            }
        } else if (index == 1) {
            if (needToShift) {
                copy(competitors = arrayOf(competitors[0], CompScore(competitor, Score())))
            } else {
                copy(competitors = competitors + CompScore(competitor, Score()))
            }
        } else {
            this
        }
    }

    fun pushCompetitor(competitor: Competitor): FightDescription {
        if (competitor.id == "fake") {
            return this
        }
        if (competitors.size < 2) {
            return copy(competitors = competitors + CompScore(competitor, Score()))
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