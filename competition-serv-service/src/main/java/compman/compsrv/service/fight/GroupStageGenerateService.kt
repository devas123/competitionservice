package compman.compsrv.service.fight

import arrow.core.ListK
import arrow.core.Tuple3
import arrow.core.extensions.listk.semigroupal.semigroupal
import arrow.core.fix
import arrow.core.k
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.ScoreDTO
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class GroupStageGenerateService : FightsService() {
    override fun supportedBracketTypes(): List<BracketType> = listOf(BracketType.MULTIPLE_GROUPS, BracketType.GROUP)

    override fun generateStageFights(competitionId: String, categoryId: String,
                                     stage: StageDescriptorDTO,
                                     compssize: Int,
                                     duration: BigDecimal,
                                     competitors: List<CompetitorDTO>,
                                     outputSize: Int): List<FightDescriptionDTO> {
        if (stage.groupDescriptors.isNullOrEmpty() || stage.groupDescriptors.fold(0) { acc, gr -> acc + gr.size } != competitors.size) {
            throw IllegalArgumentException("Group descriptors are empty or total groups size is less than competitors size")
        }

        return stage.groupDescriptors.fold(0 to emptyList<FightDescriptionDTO>()) { acc, groupDescriptorDTO ->
            (acc.first + groupDescriptorDTO.size) to (acc.second + createGroupFights(competitionId, categoryId, stage.id, groupDescriptorDTO.id, duration,
                    competitors.subList(acc.first, groupDescriptorDTO.size)))
        }.second
    }

    private fun createGroupFights(competitionId: String, categoryId: String, stageId: String, groupId: String, duration: BigDecimal, competitors: List<CompetitorDTO>): List<FightDescriptionDTO> {
        val combined = ListK.semigroupal().run { competitors.k() * competitors.k() }
                .fix()
        return combined.filter { it.a.id != it.b.id }.distinctBy { sortedSetOf(it.a.id, it.b.id).joinToString() }
                .mapIndexed { ind, comps ->
                    fightDescription(competitionId, categoryId, stageId, 0, StageRoundType.GROUP, ind, duration, "Round 0 fight $ind")
                            .setScores(arrayOf(
                                    CompScoreDTO()
                                            .setCompetitor(comps.a)
                                            .setOrder(0)
                                            .setScore(ScoreDTO().setPoints(0).setPenalties(0).setAdvantages(0)),
                                    CompScoreDTO()
                                            .setCompetitor(comps.b)
                                            .setOrder(1)
                                            .setScore(ScoreDTO().setPoints(0).setPenalties(0).setAdvantages(0))))
                            .setGroupId(groupId)
                }
    }

    override fun distributeCompetitors(competitors: List<CompetitorDTO>, fights: List<FightDescriptionDTO>, bracketType: BracketType, distributionType: DistributionType): List<FightDescriptionDTO> {
        throw NotImplementedError("Competitors are distributed during the fights creation")
    }

    override fun buildStageResults(bracketType: BracketType, stageStatus: StageStatus,
                                   fights: List<FightDescriptionDTO>, stageId: String,
                                   competitionId: String,
                                   pointsAssignmentDescriptors: List<PointsAssignmentDescriptorDTO>): List<CompetitorResultDTO> {
        return when (stageStatus) {
            StageStatus.FINISHED -> {
                val competitorPointsMap = mutableMapOf<String, Tuple3<BigDecimal, BigDecimal, String>>()

                fights.forEach { fight ->
                    val pointsDescriptor = pointsAssignmentDescriptors.find { p -> p.classifier == fight.fightResult.resultType }
                    when(pointsDescriptor?.classifier) {
                        CompetitorResultType.DRAW, CompetitorResultType.BOTH_DQ -> {
                            val loser = fight.scores.firstOrNull { sc -> sc.competitor?.id != fight.fightResult.winnerId }
                            loser?.competitor?.id?.let {
                                competitorPointsMap.compute(it) { _, u ->
                                    val basis = u ?: Tuple3(BigDecimal.ZERO, BigDecimal.ZERO, fight.groupId)
                                    Tuple3(basis.a + (pointsDescriptor.points
                                            ?: BigDecimal.ZERO), basis.b + (pointsDescriptor.additionalPoints
                                            ?: BigDecimal.ZERO), basis.c)
                                }
                            }
                            competitorPointsMap.compute(fight.fightResult.winnerId) { _, u ->
                                val basis = u ?: Tuple3(BigDecimal.ZERO, BigDecimal.ZERO, fight.groupId)
                                Tuple3(basis.a + (pointsDescriptor.points
                                        ?: BigDecimal.ZERO), basis.b + (pointsDescriptor.additionalPoints
                                        ?: BigDecimal.ZERO), basis.c)
                            }
                        }
                        else -> {
                            competitorPointsMap.compute(fight.fightResult.winnerId) { _, u ->
                                val basis = u ?: Tuple3(BigDecimal.ZERO, BigDecimal.ZERO, fight.groupId)
                                Tuple3(basis.a + (pointsDescriptor?.points
                                        ?: BigDecimal.ZERO), basis.b + (pointsDescriptor?.additionalPoints
                                        ?: BigDecimal.ZERO), basis.c)
                            }
                        }
                    }
                }
                competitorPointsMap.toList()
                        .sortedByDescending { pair -> pair.second.a + pair.second.b.divide(BigDecimal.valueOf(100000)) }
                        .mapIndexed { i, e ->
                            CompetitorResultDTO()
                                    .setRound(0)
                                    .setGroupId(e.second.c)
                                    .setCompetitorId(e.first)
                                    .setPoints(e.second.a.toInt())
                                    .setPlace(i)
                                    .setStageId(stageId)
                        }
            }
            else -> emptyList()
        }
    }
}
