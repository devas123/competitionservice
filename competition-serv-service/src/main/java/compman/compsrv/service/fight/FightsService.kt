package compman.compsrv.service.fight

import arrow.core.Either
import arrow.core.flatMap
import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.service.fight.dsl.*
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

        fun generatePlaceholderCompetitorsForGroup(size: Int): List<CompetitorDTO> {
            return (0 until size).map { CompetitorDTO().setId("placeholder-$it").setPlaceholder(true) }
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

    private fun createFightId(competitionId: String, categoryId: String?, stageId: String, round: Int, number: Int, roundType: StageRoundType?, groupId: String?) = IDGenerator.fightId(competitionId, categoryId, stageId, round, number, roundType, groupId)
    protected fun fightDescription(competitionId: String, categoryId: String, stageId: String, round: Int, roundType: StageRoundType, numberInRound: Int, duration: BigDecimal, fightName: String?, groupId: String?): FightDescriptionDTO {
        return FightDescriptionDTO()
                .setId(createFightId(competitionId, categoryId, stageId, round, numberInRound, roundType, groupId))
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
                .setGroupId(groupId)
    }

    fun applyStageInputDescriptorToResultsAndFights(descriptor: StageInputDescriptorDTO,
                                                    previousStageId: String,
                                                    fightResultOptions: (stageId: String) -> List<FightResultOptionDTO>,
                                                    stageResults: (stageId: String) -> List<CompetitorStageResultDTO>,
                                                    fights: (stageId: String) -> List<FightDescriptionDTO>): List<String> {
        val program = if (!descriptor.selectors.isNullOrEmpty()) {
            descriptor.selectors.flatMap {
                it.classifier?.let { classifier ->
                    when (classifier) {
                        SelectorClassifier.FIRST_N_PLACES -> listOf(firstNPlaces(it.applyToStageId, it.selectorValue.first().toInt()))
                        SelectorClassifier.LAST_N_PLACES -> listOf(lastNPlaces(it.applyToStageId, it.selectorValue.first().toInt()))
                        SelectorClassifier.WINNER_OF_FIGHT -> it.selectorValue.map { sv -> winnerOfFight(it.applyToStageId, sv) }
                        SelectorClassifier.LOSER_OF_FIGHT -> it.selectorValue.map { sv -> loserOfFight(it.applyToStageId, sv) }
                        SelectorClassifier.PASSED_TO_ROUND -> if (it.selectorValue.size != 2) {
                            log.warn("Passed to round selector has invalid value size (should be 2 but ${it.selectorValue.size})")
                            emptyList()
                        } else {
                            listOf(passedToRound(it.applyToStageId, it.selectorValue[0].toInt(), StageRoundType.valueOf(it.selectorValue[1])))
                        }
                        SelectorClassifier.MANUAL -> listOf(manual(it.applyToStageId, it.selectorValue.toList()))
                    }
                }.orEmpty()
            }.reduce { acc, free -> acc + free }
        } else {
            firstNPlaces(previousStageId, descriptor.numberOfCompetitors!!)
        }
        log.info("The following selectors will be used to find competitors that pass to the next stage: ")
        program.log(log)
        return program.failFast(stageResults, fights, fightResultOptions).map { it.distinct() }
                .flatMap {
                    if (it.size != descriptor.numberOfCompetitors) {
                        Either.left(CompetitorSelectError.SelectedSizeNotMatch(descriptor.numberOfCompetitors, it.size))
                    } else {
                        Either.right(it)
                    }
                }.fold({
                    log.error("Error while fight result selectors: $it")
                    emptyList()
                }, { it.toList() })
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
                                   pointsAssignmentDescriptors: List<FightResultOptionDTO>): List<CompetitorStageResultDTO>
}