package compman.compsrv.service.fight

import arrow.core.Either
import arrow.core.Tuple3
import arrow.core.flatMap
import com.compmanager.compservice.jooq.tables.pojos.CompScore
import com.compmanager.model.payment.RegistrationStatus
import com.google.common.math.LongMath
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.service.fight.dsl.*
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.applyConditionalUpdate
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

        fun getWinnerId(fight: FightDescriptionDTO) = fight.fightResult?.winnerId
        fun getLoserId(fight: FightDescriptionDTO) = fight.fightResult?.winnerId?.let { wid -> fight.scores?.find { it.competitorId != wid} }?.competitorId

        tailrec fun moveFighterToSiblings(competitorId: String, fromFightId: String, referenceType: FightReferenceType, fights: List<FightDescriptionDTO>,
                                          callback: (from: String, to: String, competitorId: String) -> Unit = { _, _, _ -> }): List<FightDescriptionDTO> {
            log.info("Moving fighter $competitorId to siblings for fight: $fromFightId, $referenceType")
            val fight = fights.find { it.id == fromFightId }
                    ?: error("Did not find fight, from which we are taking the competitor, with id $fromFightId")
            assert(fight.scores.any { it.competitorId == competitorId }) { "Competitor $competitorId is not participating in fight $fight" }
            val toFightId = if (referenceType == FightReferenceType.LOSER) {
                fight.loseFight
            } else {
                fight.winFight
            }
                    ?: fights.find { fg -> fg.scores?.any { s -> s.parentFightId == fromFightId && s.parentReferenceType == referenceType } == true }?.id
            if (toFightId.isNullOrBlank()) {
                log.info("Fight $fromFightId has no references of type $referenceType. Stopping.")
                return fights
            }
            val toFight = fights.find { it.id == toFightId }
                    ?: error("Did not find fight, to which the competitor proceeds, with id $toFightId and reference $referenceType")
            assert(toFight.scores.any { it.parentFightId == fight.id }) { "Fight $fight has no reference to $toFight" }
            val updatedFights = fights.map {
                if (it.id == toFightId) {
                    log.info("Updating fight ${it.id} with competitorId $competitorId, reference $referenceType, and from fight $fromFightId ")
                    val newScores = it.scores.map { score ->
                        if (score.parentFightId == fromFightId && score.parentReferenceType == referenceType) {
                            score.setCompetitorId(competitorId)
                        } else {
                            score
                        }
                    }.toTypedArray()
                    kotlin.runCatching { callback(fromFightId, it.id, competitorId) }.fold({}, { e -> log.warn("Callback has thrown an error", e) })
                    it.copy(scores = newScores)
                } else {
                    it
                }
            }
            return if (toFight.status != FightStatus.UNCOMPLETABLE) {
                updatedFights
            } else {
                moveFighterToSiblings(competitorId, toFight.id, FightReferenceType.WINNER, updatedFights, callback)
            }
        }

        fun markAndProcessUncompletableFights(fights: List<FightDescriptionDTO>, stageStatus: StageStatus, getFightScoresById: (id: String) -> List<CompScore>?): List<FightDescriptionDTO> {
            if (stageStatus == StageStatus.APPROVED || stageStatus == StageStatus.WAITING_FOR_APPROVAL) {
                log.info("Mark uncompletable fights.")
                val uncompletableFights = fights.filter { it.id != null && !checkIfFightIsPackedOrCanBePackedEventually(it.id!!, getFightScoresById) }
                        .map { fightDescription ->
                            fightDescription.copy(status = FightStatus.UNCOMPLETABLE,
                                    fightResult = fightDescription.fightResult ?: fightDescription.scores?.firstOrNull{ !it.competitorId.isNullOrBlank() }?.competitorId?.let { FightResultDTO(it, FightResultOptionDTO.WALKOVER.id, "BYE") })
                        }
                val markedFights = fights.map { f ->
                    val updF = uncompletableFights.firstOrNull { it.id == f.id }
                    updF ?: f
                }
                return uncompletableFights.flatMap { it.scores?.map { s -> Tuple3(s.competitorId, it.id, FightReferenceType.WINNER) }.orEmpty() }.filter { !it.a.isNullOrBlank() }
                        .fold(markedFights) { acc, tuple3 -> moveFighterToSiblings(tuple3.a, tuple3.b, tuple3.c, acc) }
            }
            return fights
        }


        fun filterPreliminaryFights(outputSize: Int, fights: List<FightDescriptionDTO>, bracketType: BracketType): List<FightDescriptionDTO> {
            log.info("Filtering fights: $outputSize, fights size: ${fights.size}, brackets type: $bracketType")
            val result = when (bracketType) {
                BracketType.SINGLE_ELIMINATION -> {
                    val ceiling = LongMath.ceilingPowerOfTwo(outputSize.toLong())
                    val roundsToReturn = fights.asSequence().groupBy { it.round!! }.map { entry -> entry.key to entry.value.size }.filter { it.second * 2 > ceiling }.map { it.first }
                    fights.filter { roundsToReturn.contains(it.round) }
                }
                BracketType.GROUP -> {
                    fights
                }
                else -> TODO("Brackets type $bracketType is not supported as a preliminary stage yet.")
            }
            log.info("Filtered fights: $outputSize, result size: ${result.size}, brackets type: $bracketType")
            return result
                    .applyConditionalUpdate({ !it.winFight.isNullOrBlank() && !result.any { r -> r.id == it.winFight } }, { it.setWinFight(null) })
                    .applyConditionalUpdate({ !it.loseFight.isNullOrBlank() && !result.any { r -> r.id == it.loseFight } }, { it.setLoseFight(null) })
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
                                                 categoryId: String,
                                                 competitionId: String): List<CompetitorDTO> {
            val random = Random()
            val result = ArrayList<CompetitorDTO>()
            for (k in 1 until size + 1) {
                val email = generateEmail(random)
                result.add(CompetitorDTO()
                        .setId(IDGenerator.hashString("$competitionId/$categoryId/$email"))
                        .setEmail(email)
                        .setFirstName(names[random.nextInt(names.size)])
                        .setLastName(surnames[random.nextInt(surnames.size)])
                        .setBirthDate(Instant.now())
                        .setRegistrationStatus(RegistrationStatus.SUCCESS_CONFIRMED.name)
                        .setAcademy(AcademyDTO(IDGenerator.uid(), "Academy${random.nextInt(academies)}"))
                        .setCategories(arrayOf(categoryId))
                        .setCompetitionId(competitionId))
            }
            return result
        }

        fun generatePlaceholderCompetitorsForGroup(size: Int): List<CompetitorDTO> {
            return (0 until size).map { CompetitorDTO().setId("placeholder-$it").setPlaceholder(true) }
        }

        private fun checkIfFightCanProduceReference(fightId: String,
                                                    referenceType: FightReferenceType,
                                                    getFightScores: (id: String) -> List<CompScore>?): Boolean {
            val fightScores = getFightScores(fightId)
            log.info("Check if can produce reference: $fightScores")
            val parentFights = fightScores
                    ?.map { it.parentReferenceType?.let { r -> FightReferenceType.valueOf(r) } to it.parentFightId }.orEmpty()
                    .filter { it.first != null && !it.second.isNullOrBlank() }
            when {
                fightScores?.withCompetitors().isNullOrEmpty() -> {
                    return when (referenceType) {
                        FightReferenceType.WINNER -> {
                            parentFights.any { checkIfFightCanProduceReference(it.second, it.first!!, getFightScores) }
                        }
                        FightReferenceType.LOSER -> {
                            parentFights.filter { checkIfFightCanProduceReference(it.second, it.first!!, getFightScores) }.size >= 2
                        }
                    }
                }
                fightScores?.withCompetitors()?.size == 1 -> {
                    return when (referenceType) {
                        FightReferenceType.WINNER -> {
                            true
                        }
                        FightReferenceType.LOSER -> {
                            parentFights.any { checkIfFightCanProduceReference(it.second, it.first!!, getFightScores) }
                        }
                    }
                }
                else -> {
                    return true
                }
            }
        }

        private fun List<CompScore>.withCompetitors() = this.filterNot { it.compscoreCompetitorId.isNullOrBlank() }

        private fun checkIfFightIsPackedOrCanBePackedEventually(fightId: String, getFightScores: (id: String) -> List<CompScore>?): Boolean {
            val scores = getFightScores(fightId).orEmpty()
            return scores.size >= 2 && scores.fold(true) { acc, sc ->
                acc && when {
                    !sc.compscoreCompetitorId.isNullOrBlank() -> true
                    !sc.parentFightId.isNullOrBlank() && sc.parentReferenceType != null -> {
                        checkIfFightCanProduceReference(sc.parentFightId, FightReferenceType.valueOf(sc.parentReferenceType), getFightScores)
                    }
                    else -> false
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
                                   stageType: StageType,
                                   fights: List<FightDescriptionDTO>,
                                   stageId: String,
                                   competitionId: String,
                                   fightResultOptions: List<FightResultOptionDTO>): List<CompetitorStageResultDTO>
}