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
import compman.compsrv.util.orZero
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class GroupStageGenerateService : FightsService() {

    companion object {



        fun createCompscore(competitorId: String?, placeHolderId: String?, order: Int): CompScoreDTO {
            return CompScoreDTO()
                    .setCompetitorId(competitorId)
                    .setPlaceholderId(placeHolderId)
                    .setOrder(order)
                    .setScore(createEmptyScore())

        }

        private fun getCompetitorId(competitor: CompetitorDTO): String? = if (competitor.isPlaceholder) null else competitor.id
        private fun getPlaceHolderId(competitor: CompetitorDTO): String? = if (competitor.isPlaceholder) competitor.id else "placeholder-${competitor.id}"

        private fun createGroupFights(competitionId: String, categoryId: String, stageId: String, groupId: String, duration: BigDecimal, competitors: List<CompetitorDTO>): List<FightDescriptionDTO> {
            val combined = createPairs(competitors)
            return combined.filter { it.a.id != it.b.id }.distinctBy { sortedSetOf(it.a.id, it.b.id).joinToString() }
                    .mapIndexed { ind, comps ->
                        fightDescription(competitionId = competitionId,
                                categoryId = categoryId,
                                stageId = stageId, round =  0,
                                roundType = StageRoundType.GROUP,
                                numberInRound = ind,
                                duration = duration, fightName = "Round 0 fight $ind",
                                groupId = groupId)
                                .setScores(arrayOf(
                                        createCompscore(getCompetitorId(comps.a), getPlaceHolderId(comps.a), 0),
                                        createCompscore(getCompetitorId(comps.b), getPlaceHolderId(comps.b), 1)
                                ))
                    }
        }

        fun <T> createPairs(competitors: List<T>, competitors2: List<T> = competitors) =
                ListK.semigroupal().run { competitors.k() * competitors2.k() }
                        .fix()

    }

    override fun supportedBracketTypes(): List<BracketType> = listOf(BracketType.GROUP)

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
            throw IllegalArgumentException("Group descriptors are empty (${stage.groupDescriptors?.size}) or total groups size (${stage.groupDescriptors.fold(0) { acc, gr -> acc + gr.size }}) does not match the competitors (${comps.size}) size")
        }

        val fights = stage.groupDescriptors.fold(0 to emptyList<FightDescriptionDTO>()) { acc, groupDescriptorDTO ->
            (acc.first + groupDescriptorDTO.size) to (acc.second + createGroupFights(competitionId, categoryId, stage.id, groupDescriptorDTO.id, duration,
                    comps.subList(acc.first, acc.first + groupDescriptorDTO.size)))
        }.second

        return validateFights(fights)
    }


    override fun distributeCompetitors(competitors: List<CompetitorDTO>, fights: List<FightDescriptionDTO>, bracketType: BracketType): List<FightDescriptionDTO> {
        validateFights(fights)
        val placeholders = fights.flatMap { f -> f.scores.map { it.placeholderId } }.distinct()
        val fightsWithDistributedCompetitors = competitors.foldIndexed(fights) { index, acc, competitor ->
            if (index < placeholders.size) {
                val placeholderId = placeholders[index]
                acc.map { f ->
                    f.setScores(f.scores.map { s ->
                        if (s.placeholderId == placeholderId) {
                            s.setCompetitorId(competitor.id)
                        } else {
                            s
                        }
                    }.toTypedArray())
                }
            } else {
                acc
            }
        }
        return validateFights(fightsWithDistributedCompetitors)
    }

    private fun validateFights(fights: List<FightDescriptionDTO>): List<FightDescriptionDTO> {
        assert(fights.all { it.scores?.size == 2 }) { "Some fights do not have scores. Something is wrong." }
        assert(fights.all { it.scores.all { scoreDTO -> !scoreDTO.placeholderId.isNullOrBlank() || !scoreDTO.competitorId.isNullOrBlank() } }) { "Not all fights have placeholders or real competitors assigned." }
        return fights
    }

    override fun buildStageResults(bracketType: BracketType, stageStatus: StageStatus,
                                   stageType: StageType,
                                   fights: List<FightDescriptionDTO>, stageId: String,
                                   competitionId: String,
                                   fightResultOptions: List<FightResultOptionDTO>): List<CompetitorStageResultDTO> {
        return when (stageStatus) {
            StageStatus.FINISHED -> {
                val competitorPointsMap = mutableMapOf<String, Tuple3<BigDecimal, BigDecimal, String>>()
                fights.forEach { fight ->
                    val pointsDescriptor = fightResultOptions.find { p -> p.id == fight.fightResult.resultTypeId }
                    when (pointsDescriptor?.isDraw) {
                        true -> {
                            fight.scores.forEach { sc ->
                                sc.competitorId?.let {
                                    competitorPointsMap.compute(it) { _, u ->
                                        val basis = u ?: Tuple3(BigDecimal.ZERO, BigDecimal.ZERO, fight.groupId)
                                        Tuple3(basis.a + pointsDescriptor.winnerPoints.orZero(), basis.b + pointsDescriptor.winnerAdditionalPoints.orZero(), basis.c)
                                    }
                                }
                            }
                        }
                        else -> {
                            competitorPointsMap.compute(fight.fightResult.winnerId) { _, u ->
                                val basis = u ?: Tuple3(BigDecimal.ZERO, BigDecimal.ZERO, fight.groupId)
                                Tuple3(basis.a + pointsDescriptor?.winnerPoints.orZero(), basis.b + pointsDescriptor?.winnerAdditionalPoints.orZero(), basis.c)
                            }
                            fight.scores.find { it.competitorId != fight.fightResult.winnerId }?.competitorId?.let {
                                competitorPointsMap.compute(it) { _, u ->
                                    val basis = u ?: Tuple3(BigDecimal.ZERO, BigDecimal.ZERO, fight.groupId)
                                    Tuple3(basis.a + pointsDescriptor?.loserPoints.orZero(), basis.b + pointsDescriptor?.loserAdditionalPoints.orZero(), basis.c)
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
