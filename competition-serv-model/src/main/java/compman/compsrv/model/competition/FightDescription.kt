package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.data.annotation.PersistenceConstructor
import java.time.Instant
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class FightDescription
@JsonCreator
@PersistenceConstructor
constructor(@JsonProperty("fightId") val fightId: String,
            @JsonProperty("categoryId") val categoryId: String,
            @JsonProperty("winFight") val winFight: String?,
            @JsonProperty("loseFight") val loseFight: String?,
            @JsonProperty("competitors") val competitors: Array<CompScore>,
            @JsonProperty("parentId1") val parentId1: String?,
            @JsonProperty("parentId2") val parentId2: String?,
            @JsonProperty("duration") val duration: Long?,
            @JsonProperty("round") val round: Int?,
            @JsonProperty("stage") val stage: FightStage?,
            @JsonProperty("fightResult") val fightResult: FightResult?,
            @JsonProperty("matId") val matId: String?,
            @JsonProperty("numberOnMat") val numberOnMat: Int?,
            @JsonProperty("priority") val priority: Int,
            @JsonProperty("competitionId") val competitionId: String,
            @JsonProperty("period") val period: String,
            @JsonProperty("startTime") val startTime: Date?,
            @JsonProperty("numberInRound") val numberInRound: Int) {

    constructor(fightId: String, categoryId: String, competitionId: String) : this(fightId = fightId,
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
            startTime = Date.from(Instant.EPOCH),
            priority = 0,
            period = "",
            competitionId = competitionId,
            numberInRound = 0)

    constructor(fightId: String, categoryId: String, round: Int, competitionId: String, numberInRound: Int) : this(fightId = fightId,
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
            startTime = Date.from(Instant.EPOCH))

    constructor(fightId: String, categoryId: String, round: Int, winFight: String?, competitionId: String, numberInRound: Int) : this(fightId = fightId,
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
            startTime = Date.from(Instant.EPOCH))

    fun setStartTime(startTime: Date) = copy(startTime = startTime)
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
                copy(competitors = arrayOf(CompScore(competitor, Score(competitor.id)), competitors[1]))
            } else {
                copy(competitors = arrayOf(CompScore(competitor, Score(competitor.id)), competitors[0]))
            }
        } else if (index == 1) {
            if (needToShift) {
                copy(competitors = arrayOf(competitors[0], CompScore(competitor, Score(competitor.id))))
            } else {
                copy(competitors = competitors + CompScore(competitor, Score(competitor.id)))
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
            return copy(competitors = competitors + CompScore(competitor, Score(competitor.id)))
        } else {
            throw RuntimeException("Fight is already packed. Cannot add competitors")
        }
    }

    fun setDuration(duration: Long) = copy(duration = duration)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FightDescription

        if (fightId != other.fightId) return false
        if (categoryId != other.categoryId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fightId.hashCode()
        result = 31 * result + categoryId.hashCode()
        return result
    }
}