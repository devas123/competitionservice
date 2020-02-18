package compman.compsrv.service.fight

import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.util.IDGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.util.*

abstract class FightsService {
    companion object {
        private val finishedStatuses = listOf(FightStatus.UNCOMPLETABLE, FightStatus.FINISHED, FightStatus.WALKOVER)
        val unMovableFightStatuses = finishedStatuses + FightStatus.IN_PROGRESS
        val notFinishedStatuses = listOf(FightStatus.PENDING, FightStatus.IN_PROGRESS, FightStatus.GET_READY, FightStatus.PAUSED)
        const val SEMI_FINAL = "Semi-final"
        const val QUARTER_FINAL = "Quarter-final"
        const val FINAL = "Final"
        const val WINNER_FINAL = "Winner-final"
        const val GRAND_FINAL = "Grand final"
        const val ELIMINATION = "Elimination"
        const val THIRD_PLACE_FIGHT = "Third place"
        private val names = arrayOf("Vasya", "Kolya", "Petya", "Sasha", "Vanya", "Semen", "Grisha", "Kot", "Evgen", "Prohor", "Evgrat", "Stas", "Andrey", "Marina")
        private val surnames = arrayOf("Vasin", "Kolin", "Petin", "Sashin", "Vanin", "Senin", "Grishin", "Kotov", "Evgenov", "Prohorov", "Evgratov", "Stasov", "Andreev", "Marinin")
        private val validChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray()

        private fun generateRandomString(chars: CharArray, random: Random, length: Int): String {
            tailrec fun loop(result: StringBuilder, chars: CharArray, length: Int, random: Random): String {
                return if (result.length >= length) {
                    result.toString()
                } else {
                    loop(result.append(chars[random.nextInt(chars.size)]), chars, length, random)
                }
            }
            return loop(StringBuilder(), chars, length, random)
        }

        private fun generateEmail(random: Random): String {
            val emailBuilder = StringBuilder()
            return emailBuilder
                    .append(generateRandomString(validChars, random, 10)).append("@")
                    .append(generateRandomString(validChars, random, 7)).append(".")
                    .append(generateRandomString(validChars, random, 4)).toString()
        }

        fun generateRandomCompetitorsForCategory(size: Int, academies: Int = 20,
                                                 category: CategoryDescriptorDTO,
                                                 competitionId: String): List<CompetitorDTO> {
            val random = Random()
            val result = ArrayList<CompetitorDTO>()
            for (k in 1 until size + 1) {
                val email = generateEmail(random)
                result.add(CompetitorDTO()
                        .setId(IDGenerator.hashString("$competitionId/${category.id}/$email"))
                        .setEmail(email)
                        .setFirstName(names[random.nextInt(names.size)])
                        .setLastName(surnames[random.nextInt(surnames.size)])
                        .setBirthDate(Instant.now())
                        .setRegistrationStatus(RegistrationStatus.SUCCESS_CONFIRMED.name)
                        .setAcademy(AcademyDTO(UUID.randomUUID().toString(), "Academy${random.nextInt(academies)}"))
                        .setCategories(arrayOf(category.id))
                        .setCompetitionId(competitionId))
            }
            return result
        }

        private fun checkIfFightCanProduceReference(fightId: String,
                                                    referenceType: FightReferenceType,
                                                    getFight: (id: String) -> FightDescriptionDTO): Boolean {
            val fight = getFight(fightId)
            val parentFights = listOf(fight.parentId1?.referenceType to fight.parentId1?.fightId?.let { getFight(it) },
                    fight.parentId2?.referenceType to fight.parentId2?.fightId?.let { getFight(it) }).filter { it.first != null && it.second != null }

            when {
                fight.scores.isNullOrEmpty() -> {
                    return when (referenceType) {
                        FightReferenceType.WINNER -> {
                            parentFights.any { canProduceReferenceToChild(it, fight, getFight) }
                        }
                        FightReferenceType.LOSER -> {
                            parentFights.size >= 2 && parentFights.all { canProduceReferenceToChild(it, fight, getFight) }
                        }
                    }

                }
                fight.scores!!.size == 1 -> {
                    return when (referenceType) {
                        FightReferenceType.WINNER -> {
                            true
                        }
                        FightReferenceType.LOSER -> {
                            parentFights.isNotEmpty() && parentFights.any { canProduceReferenceToChild(it, fight, getFight) }
                        }
                    }
                }
                else -> {
                    return true
                }
            }
        }

        private fun canProduceReferenceToChild(it: Pair<FightReferenceType?, FightDescriptionDTO?>, child: FightDescriptionDTO, getFight: (id: String) -> FightDescriptionDTO): Boolean {
            return ((it.second?.scores?.all { sc -> child.scores!!.none { compScore -> compScore.competitor.id == sc.competitor.id } } == true)
                    && checkIfFightCanProduceReference(it.second?.id!!, it.first!!, getFight))
        }

        fun checkIfFightCanBePacked(fightId: String, getFight: (id: String) -> FightDescriptionDTO): Boolean {
            val fight = getFight(fightId)
            return when {
                fight.scores.isNullOrEmpty() -> {
                    false
                }
                fight.scores!!.size >= 2 -> {
                    true
                }
                else -> {
                    listOfNotNull(fight.parentId1, fight.parentId2)
                            .map { it.referenceType to it.fightId?.let { id -> getFight(id) } }
                            .filter { it.first != null && it.second != null }
                            .filter { it.second!!.scores!!.none { sc -> fight.scores!!.any { fsc -> fsc.competitor.id == sc.competitor.id } } }
                            .filter { checkIfFightCanProduceReference(it.second?.id!!, it.first!!, getFight) }
                            .size + (fight.scores?.size ?: 0) >= 2
                }
            }
        }
    }

    protected val log: Logger = LoggerFactory.getLogger(FightsService::class.java)

    private fun createFightId(competitionId: String, categoryId: String?, stageId: String, round: Int, number: Int, roundType: StageRoundType?) = IDGenerator.fightId(competitionId, categoryId, stageId, round, number, roundType)
    protected fun fightDescription(competitionId: String, categoryId: String, stageId: String, round: Int, roundType: StageRoundType, numberInRound: Int, duration: BigDecimal, fightName: String?): FightDescriptionDTO {
        return FightDescriptionDTO()
                .setId(createFightId(competitionId, categoryId, stageId, round, numberInRound, roundType))
                .setCategoryId(categoryId)
                .setRound(round)
                .setNumberInRound(numberInRound)
                .setCompetitionId(competitionId)
                .setDuration(duration)
                .setRoundType(roundType)
                .setStageId(stageId)
                .setFightName(fightName)
                .setStatus(FightStatus.PENDING)
                .setPriority(0)
    }

    fun applyStageInputDescriptorToResultsAndFights(descriptor: StageInputDescriptorDTO,
                                                    results: List<CompetitorResultDTO>,
                                                    fights: List<FightDescriptionDTO>): List<String> {
        fun selectWinnerIdOfFight(fightId: String) = fights.first {
            it.id == fightId && it.fightResult!!.resultType != CompetitorResultType.BOTH_DQ
                    && it.fightResult!!.resultType != CompetitorResultType.DRAW
        }.fightResult?.winnerId

        fun selectLoserIdOfFight(fightId: String): String? {
            val fight = fights.first {
                it.id == fightId && it.fightResult!!.resultType != CompetitorResultType.BOTH_DQ
                        && it.fightResult!!.resultType != CompetitorResultType.DRAW
            }
            return fight.scores?.first { it.competitor.id != fight.fightResult!!.winnerId }?.competitor?.id
        }

        fun findWinnersOrLosers(selector: CompetitorSelectorDTO, selectorFun: (fightId: String) -> String?, results: List<CompetitorResultDTO>): List<CompetitorResultDTO> {
            val selectorVal = selector.selectorValue!!
            val selectedFighterIds = selectorVal.mapNotNull { selectorFun.invoke(it) }
            return results.filter { selectedFighterIds.contains(it.competitorId) }
        }

        fun filterResults(selector: CompetitorSelectorDTO, results: List<CompetitorResultDTO>): List<CompetitorResultDTO> {
            return when (selector.classifier!!) {
                SelectorClassifier.FIRST_N_PLACES -> {
                    val selectorVal = selector.selectorValue?.first()!!.toInt()
                    results.sortedBy { it.place!! }.take(selectorVal)
                }
                SelectorClassifier.LAST_N_PLACES -> {
                    val selectorVal = selector.selectorValue?.first()!!.toInt()
                    results.sortedBy { it.place!! }.takeLast(selectorVal)
                }
                SelectorClassifier.WINNER_OF_FIGHT -> {
                    findWinnersOrLosers(selector, ::selectWinnerIdOfFight, results)
                }
                SelectorClassifier.LOSER_OF_FIGHT -> {
                    findWinnersOrLosers(selector, ::selectLoserIdOfFight, results)
                }
                SelectorClassifier.PASSED_TO_ROUND -> {
                    val selectorVal = selector.selectorValue?.first()!!.toInt()
                    results.filter { it.round!! >= selectorVal }
                }
            }
        }
        return filterResults(descriptor.selectors!!.first(), results).mapNotNull { it.competitorId }.take(descriptor.numberOfCompetitors)
    }

    abstract fun supportedBracketTypes(): List<BracketType>

    abstract fun generateStageFights(competitionId: String, categoryId: String, stage: StageDescriptorDTO,
                                     compssize: Int, duration: BigDecimal, competitors: List<CompetitorDTO>, outputSize: Int): List<FightDescriptionDTO>

    abstract fun distributeCompetitors(competitors: List<CompetitorDTO>, fights: List<FightDescriptionDTO>, bracketType: BracketType, distributionType: DistributionType = DistributionType.RANDOM): List<FightDescriptionDTO>
    abstract fun buildStageResults(bracketType: BracketType,
                                   stageStatus: StageStatus,
                                   fights: List<FightDescriptionDTO>,
                                   stageId: String,
                                   competitionId: String,
                                   pointsAssignmentDescriptors: List<PointsAssignmentDescriptorDTO>): List<CompetitorResultDTO>
}