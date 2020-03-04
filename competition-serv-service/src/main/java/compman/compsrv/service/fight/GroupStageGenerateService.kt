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

    override fun generateStageFights(competitionId: String,
                                     categoryId: String,
                                     stage: StageDescriptorDTO,
                                     compssize: Int,
                                     duration: BigDecimal,
                                     competitors: List<CompetitorDTO>,
                                     outputSize: Int): List<FightDescriptionDTO> {
        val comps = when (stage.stageOrder) {
            0 -> competitors
            else -> {
                if (stage.inputDescriptor.numberOfCompetitors <= competitors.size) {
                    competitors.take(stage.inputDescriptor.numberOfCompetitors)
                } else {
                    competitors + generatePlaceholderCompetitorsForGroup(stage.inputDescriptor.numberOfCompetitors - competitors.size)
                }
            }
        }
        if (stage.groupDescriptors.isNullOrEmpty() || stage.groupDescriptors.fold(0) { acc, gr -> acc + gr.size } != comps.size) {
            throw IllegalArgumentException("Group descriptors are empty or total groups size is less than competitors size")
        }

        return stage.groupDescriptors.fold(0 to emptyList<FightDescriptionDTO>()) { acc, groupDescriptorDTO ->
            (acc.first + groupDescriptorDTO.size) to (acc.second + createGroupFights(competitionId, categoryId, stage.id, groupDescriptorDTO.id, duration,
                    comps.subList(acc.first, acc.first + groupDescriptorDTO.size)))
        }.second
    }

    private fun createCompscore(competitor: CompetitorDTO, order: Int): CompScoreDTO {
        return if (!competitor.isPlaceholder) {
             CompScoreDTO()
                    .setCompetitor(competitor)
                    .setOrder(order)
                    .setScore(ScoreDTO().setPoints(0).setPenalties(0).setAdvantages(0))
        } else {
            CompScoreDTO()
                    .setPlaceholderId(competitor.id)
                    .setOrder(order)
                    .setScore(ScoreDTO().setPoints(0).setPenalties(0).setAdvantages(0))
        }
    }

    private fun createGroupFights(competitionId: String, categoryId: String, stageId: String, groupId: String, duration: BigDecimal, competitors: List<CompetitorDTO>): List<FightDescriptionDTO> {
        val combined = ListK.semigroupal().run { competitors.k() * competitors.k() }
                .fix()
        return combined.filter { it.a.id != it.b.id }.distinctBy { sortedSetOf(it.a.id, it.b.id).joinToString() }
                .mapIndexed { ind, comps ->
                    fightDescription(competitionId, categoryId, stageId, 0, StageRoundType.GROUP, ind, duration, "Round 0 fight $ind", groupId)
                            .setScores(arrayOf(
                                    createCompscore(comps.a, 0),
                                    createCompscore(comps.b, 1)
                            ))
                }
    }

    override fun distributeCompetitors(competitors: List<CompetitorDTO>, fights: List<FightDescriptionDTO>, bracketType: BracketType, distributionType: DistributionType): List<FightDescriptionDTO> {
        throw NotImplementedError("Competitors are distributed during the fights creation")
    }

    override fun buildStageResults(bracketType: BracketType, stageStatus: StageStatus,
                                   fights: List<FightDescriptionDTO>, stageId: String,
                                   competitionId: String,
                                   pointsAssignmentDescriptors: List<FightResultOptionDTO>): List<CompetitorStageResultDTO> {
        return when (stageStatus) {
            StageStatus.FINISHED -> {
                val competitorPointsMap = mutableMapOf<String, Tuple3<BigDecimal, BigDecimal, String>>()

                fights.forEach { fight ->
                    val pointsDescriptor = pointsAssignmentDescriptors.find { p -> p.id == fight.fightResult.resultTypeId }
                    when (pointsDescriptor?.isDraw) {
                        true -> {
                            fight.scores.forEach { sc ->
                                sc.competitor?.id?.let {
                                    competitorPointsMap.compute(it) { _, u ->
                                        val basis = u ?: Tuple3(BigDecimal.ZERO, BigDecimal.ZERO, fight.groupId)
                                        Tuple3(basis.a + (pointsDescriptor.winnerPoints
                                                ?: BigDecimal.ZERO), basis.b + (pointsDescriptor.winnerAdditionalPoints
                                                ?: BigDecimal.ZERO), basis.c)
                                    }
                                }
                            }
                        }
                        else -> {
                            competitorPointsMap.compute(fight.fightResult.winnerId) { _, u ->
                                val basis = u ?: Tuple3(BigDecimal.ZERO, BigDecimal.ZERO, fight.groupId)
                                Tuple3(basis.a + (pointsDescriptor?.winnerPoints
                                        ?: BigDecimal.ZERO), basis.b + (pointsDescriptor?.winnerAdditionalPoints
                                        ?: BigDecimal.ZERO), basis.c)
                            }

                            fight.scores.find { it.competitor?.id != fight.fightResult.winnerId }?.competitor?.id?.let {
                                competitorPointsMap.compute(it) { _, u ->
                                    val basis = u ?: Tuple3(BigDecimal.ZERO, BigDecimal.ZERO, fight.groupId)
                                    Tuple3(basis.a + (pointsDescriptor?.winnerPoints
                                            ?: BigDecimal.ZERO), basis.b + (pointsDescriptor?.winnerAdditionalPoints
                                            ?: BigDecimal.ZERO), basis.c)
                                }
                            }
                        }
                    }
                }
                competitorPointsMap.toList()
                        .sortedByDescending { pair -> pair.second.a + pair.second.b.divide(BigDecimal.valueOf(100000)) }
                        .mapIndexed { i, e ->
                            CompetitorStageResultDTO()
                                    .setRound(0)
                                    .setGroupId(e.second.c)
                                    .setCompetitorId(e.first)
                                    .setPoints(e.second.a)
                                    .setPlace(i)
                                    .setStageId(stageId)
                        }
            }
            else -> emptyList()
        }
    }
}
