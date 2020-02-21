package compman.compsrv.util

import compman.compsrv.model.dto.brackets.ParentFightReferenceDTO
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
                             parentId1: ParentFightReferenceDTO? = this.parentId1,
                             parentId2: ParentFightReferenceDTO? = this.parentId2,
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
                             stageId: String? = this.stageId): FightDescriptionDTO = FightDescriptionDTO()
        .setId(id)
        .setFightName(fightName)
        .setCategoryId(categoryId)
        .setWinFight(winFight)
        .setLoseFight(loseFight)
        .setScores(scores)
        .setParentId1(parentId1)
        .setParentId2(parentId2)
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

fun FightDescriptionDTO.pushCompetitor(competitor: CompetitorDTO): FightDescriptionDTO {
    if (competitor.id == "fake") {
        return this
    }
    val localScores = mutableListOf<CompScoreDTO>().apply { scores?.toList()?.let { this.addAll(it) } }
    if (localScores.size < 2) {
        localScores.add(CompScoreDTO().setCompetitor(competitor).setScore(ScoreDTO()).setOrder(localScores.size))
    } else {
        throw RuntimeException("Fight is already packed. Cannot add competitors")
    }
    return copy(scores = localScores.toTypedArray())
}
