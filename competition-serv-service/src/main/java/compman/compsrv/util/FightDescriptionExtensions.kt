package compman.compsrv.util

import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import java.math.BigDecimal
import java.time.Instant

fun FightDescriptionDTO.copy(id: String = this.id,
                             categoryId: String = this.categoryId,
                             fightName: String? = this.fightName,
                             winFight: String? = this.winFight,
                             loseFight: String? = this.loseFight,
                             scores: Array<CompScoreDTO>? = this.scores,
                             duration: BigDecimal = this.duration,
                             round: Int = this.round,
                             roundType: StageRoundType = this.roundType,
                             status: FightStatus? = this.status,
                             fightResult: FightResultDTO? = this.fightResult,
                             mat: MatDescriptionDTO? = this.mat,
                             numberOnMat: Int? = this.numberOnMat,
                             priority: Int? = this.priority,
                             competitionId: String = this.competitionId,
                             period: String? = this.period,
                             startTime: Instant? = this.startTime,
                             numberInRound: Int? = this.numberInRound,
                             groupId: String? = this.groupId,
                             invalid: Boolean? = this.invalid,
                             stageId: String? = this.stageId): FightDescriptionDTO = FightDescriptionDTO()
        .setId(id)
        .setFightName(fightName)
        .setCategoryId(categoryId)
        .setWinFight(winFight)
        .setLoseFight(loseFight)
        .setScores(scores)
        .setDuration(duration)
        .setCompetitionId(competitionId)
        .setRound(round)
        .setRoundType(roundType)
        .setStatus(status)
        .setStartTime(startTime)
        .setFightResult(fightResult)
        .setMat(mat)
        .setNumberOnMat(numberOnMat)
        .setNumberInRound(numberInRound)
        .setPriority(priority)
        .setPeriod(period)
        .setStageId(stageId)
        .setGroupId(groupId)
        .setInvalid(invalid)

fun FightDescriptionDTO.pushCompetitor(competitorId: String, fromFight: String? = null): FightDescriptionDTO {
    if (competitorId == "fake") {
        return this
    }
    fromFight?.let { id ->
        val score = scores.find { it.parentFightId == id } ?: error("Fight ${this.id} has no reference from fight $id")
        return copy(scores = scores.map {
            if (it.parentFightId == score.parentFightId && it.order == score.order) {
                score.setCompetitorId(competitorId)
            } else {
                it
            }
        }.toTypedArray())
    }
    val localScores = mutableListOf<CompScoreDTO>().apply { scores?.toList()?.let { this.addAll(it) } }
    when {
        localScores.size < 2 -> {
            localScores.add(CompScoreDTO().setCompetitorId(competitorId).setScore(ScoreDTO()).setOrder(localScores.size))
            return copy(scores = localScores.toTypedArray())
        }
        localScores.any { it.parentFightId.isNullOrBlank() && it.competitorId.isNullOrBlank() } -> {
            val score = localScores.first { it.competitorId.isNullOrBlank() && it.parentFightId.isNullOrBlank() }
            return copy(scores = scores.map {
                if (it.order == score.order) {
                    score.setCompetitorId(competitorId)
                } else {
                    it
                }
            }.toTypedArray())
        }
        else -> {
            throw RuntimeException("Fight is already packed. Cannot add competitors")
        }
    }
}
