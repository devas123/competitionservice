package compman.compsrv.service.fight

import arrow.core.Either
import arrow.core.flatMap
import com.compmanager.model.payment.RegistrationStatus
import com.google.common.math.DoubleMath
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.service.fight.dsl.*
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.copy
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
        fun createEmptyScore(): ScoreDTO = ScoreDTO().setAdvantages(0).setPenalties(0).setPoints(0).setPointGroups(emptyArray())

        fun upsertFights(updates: List<FightDescriptionDTO>, target: List<FightDescriptionDTO>): List<FightDescriptionDTO> {
            return target.map { f ->
                updates.firstOrNull { it.id == f.id } ?: f
            } + updates.filter { f -> target.none { tf -> tf.id == f.id } }
        }

        fun markUncompletableFights(dirtyFights: List<FightDescriptionDTO>, getFightById: (id: String) -> FightDescriptionDTO?): List<FightDescriptionDTO> {
            log.info("Mark uncompletable fights.")
            val fights = dirtyFights.map {
                if (it.status == FightStatus.UNCOMPLETABLE) {
                    it.setStatus(FightStatus.PENDING)
                } else {
                    it
                }
            }
            val firstRoundUncompletableFightsFights = fights.filter { it.id != null && !checkIfFightIsPackedOrCanBePackedEventually(it.id!!, getFightById) }
                    .map { fightDescription ->
                        fightDescription.copy(status = FightStatus.UNCOMPLETABLE,
                                fightResult = FightResultDTO(fightDescription.scores?.firstOrNull()?.competitorId, null, "BYE"))
                    }
            return fights.map { f ->
                val updF = firstRoundUncompletableFightsFights.firstOrNull { it.id == f.id }
                updF ?: f
            }
        }


        fun filterPreliminaryFights(outputSize: Int, fights: List<FightDescriptionDTO>, bracketType: BracketType): List<FightDescriptionDTO> {
            log.info("Filtering fights: $outputSize, fights size: ${fights.size}, brackets type: $bracketType")
            val result = when (bracketType) {
                BracketType.SINGLE_ELIMINATION -> {
                    if (outputSize == 3) {
                        val thirdPlaceFight = fights.firstOrNull { it.roundType == StageRoundType.THIRD_PLACE_FIGHT }
                        assert(thirdPlaceFight != null) { "There is no fight for third place, but the output of the stage is 3, cannot calculate." }
                        val grandFinal = fights.firstOrNull { it.roundType == StageRoundType.GRAND_FINAL }
                        fights.filter { it.id != grandFinal?.id }
                    } else {
                        assert(DoubleMath.isPowerOfTwo(outputSize.toDouble())) { "Output for single elimination brackets must be power of two, but it is $outputSize" }
                        val roundsToReturn = fights.asSequence().groupBy { it.round!! }.map { entry -> entry.key to entry.value.size }.filter { it.second * 2 > outputSize }.map { it.first }
                        fights.filter { roundsToReturn.contains(it.round) }
                    }
                }
                BracketType.GROUP -> {
                    fights
                }
                else -> TODO("Brackets type $bracketType is not supported as a preliminary stage.")
            }
            log.info("Filtered fights: $outputSize, result size: ${result.size}, brackets type: $bracketType")
            return result
        }


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
                                                    getFight: (id: String) -> FightDescriptionDTO?): Boolean {
            val fight = getFight(fightId)
            log.info("Check if can produce reference: $fight")
            val parentFights = listOf(fight?.parentId1?.referenceType to fight?.parentId1?.fightId?.let { getFight(it) },
                    fight?.parentId2?.referenceType to fight?.parentId2?.fightId?.let { getFight(it) }).filter { it.first != null && it.second != null }

            when {
                fight?.scores.isNullOrEmpty() -> {
                    return when (referenceType) {
                        FightReferenceType.WINNER -> {
                            parentFights.any { canProduceReferenceToChild(it, fight, getFight) }
                        }
                        FightReferenceType.LOSER -> {
                            parentFights.size >= 2 && parentFights.all { canProduceReferenceToChild(it, fight, getFight) }
                        }
                    }

                }
                fight?.scores!!.size == 1 -> {
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

        private fun canProduceReferenceToChild(it: Pair<FightReferenceType?, FightDescriptionDTO?>, child: FightDescriptionDTO?, getFight: (id: String) -> FightDescriptionDTO?): Boolean {
            log.info("Check if can produce reference to a child: $it")

            return ((it.second?.scores?.all { sc ->
                child?.scores.orEmpty().none { compScore ->
                    compScore.competitorId == sc.competitorId
                }
            } == true)
                    && checkIfFightCanProduceReference(it.second?.id!!, it.first!!, getFight))
        }

        private fun Array<CompScoreDTO>.withCompetitors() = this.filterNot { it.competitorId.isNullOrBlank() }

        fun checkIfFightIsPackedOrCanBePackedEventually(fightId: String, getFight: (id: String) -> FightDescriptionDTO?): Boolean {
            val fight = getFight(fightId)
            val effectiveScores = fight?.scores?.withCompetitors() ?: emptyList()
            return when {
                effectiveScores.size >= 2 -> {
                    true
                }
                else -> {
                    listOfNotNull(fight?.parentId1, fight?.parentId2)
                            .map { it.referenceType to it.fightId?.let { id -> getFight(id) } }
                            .filter { it.first != null && it.second != null }
                            .filter {
                                it.second!!.scores?.none { sc ->
                                    effectiveScores.any { fsc ->
                                        fsc.competitorId == sc.competitorId
                                    }
                                } != false
                            }
                            .filter { checkIfFightCanProduceReference(it.second?.id!!, it.first!!, getFight) }.size + effectiveScores.size >= 2
                }
            }
        }

        fun fightDescription(competitionId: String, categoryId: String, stageId: String, round: Int, roundType: StageRoundType, numberInRound: Int, duration: BigDecimal, fightName: String?, groupId: String?): FightDescriptionDTO {
            return FightDescriptionDTO()
                    .setId(createFightId(stageId, groupId))
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
                    .setFightName("Round ${round + 1} fight ${numberInRound + 1}")
        }

        private fun createFightId(stageId: String, groupId: String?) = IDGenerator.fightId(stageId, groupId)


        val log: Logger = LoggerFactory.getLogger(FightsService::class.java)

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

    abstract fun distributeCompetitors(competitors: List<CompetitorDTO>, fights: List<FightDescriptionDTO>, bracketType: BracketType): List<FightDescriptionDTO>
    abstract fun buildStageResults(bracketType: BracketType,
                                   stageStatus: StageStatus,
                                   fights: List<FightDescriptionDTO>,
                                   stageId: String,
                                   competitionId: String,
                                   pointsAssignmentDescriptors: List<FightResultOptionDTO>): List<CompetitorStageResultDTO>
}