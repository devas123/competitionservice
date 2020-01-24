package compman.compsrv.service

import arrow.core.Tuple3
import arrow.core.extensions.list.foldable.nonEmpty
import arrow.core.extensions.list.zip.zipWith
import com.compmanager.model.payment.RegistrationStatus
import com.google.common.math.DoubleMath
import com.google.common.math.IntMath
import com.google.common.math.LongMath
import compman.compsrv.jpa.brackets.CompetitorResult
import compman.compsrv.jpa.brackets.CompetitorSelector
import compman.compsrv.jpa.brackets.StageInputDescriptor
import compman.compsrv.jpa.competition.*
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.FightStage
import compman.compsrv.model.exceptions.CategoryNotFoundException
import compman.compsrv.repository.CategoryDescriptorCrudRepository
import compman.compsrv.util.IDGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.*
import kotlin.math.max

@Component
class FightsGenerateService(private val categoryCrudRepository: CategoryDescriptorCrudRepository) {

    companion object {
        const val SEMI_FINAL = "Semi-final"
        const val QUARTER_FINAL = "Quarter-final"
        const val FINAL = "Final"
        const val WINNER_FINAL = "Winner-final"
        const val GRAND_FINAL = "Grand final"
        const val ELIMINATION = "Elimination"
        const val THIRD_PLACE_FIGHT = "Third place"

        private val log = LoggerFactory.getLogger(FightsGenerateService::class.java)
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

        fun generateRandomCompetitorsForCategory(size: Int, academies: Int = 20, category: CategoryDescriptor, competitionId: String): List<Competitor> {
            val random = Random()
            val result = ArrayList<Competitor>()
            for (k in 1 until size + 1) {
                val email = generateEmail(random)
                result.add(Competitor(
                        IDGenerator.hashString("$competitionId/${category.id}/$email"),
                        email,
                        null,
                        names[random.nextInt(names.size)],
                        surnames[random.nextInt(surnames.size)],
                        Instant.now(),
                        Academy(UUID.randomUUID().toString(), "Academy${random.nextInt(academies)}"),
                        mutableSetOf(category),
                        competitionId,
                        RegistrationStatus.SUCCESS_CONFIRMED,
                        null))
            }
            return result
        }

        private fun checkIfFightCanProduceReference(fightId: String, referenceType: FightReferenceType, getFight: (id: String) -> FightDescription): Boolean {
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
        private fun canProduceReferenceToChild(it: Pair<FightReferenceType?, FightDescription?>, child: FightDescription, getFight: (id: String) -> FightDescription): Boolean {
            val result = (it.second?.scores?.all { sc -> child.scores!!.none { compScore -> compScore.competitor.id == sc.competitor.id } } == true) && checkIfFightCanProduceReference(it.second?.id!!, it.first!!, getFight)
            log.info("checking if fight ${it.second} \ncan produce reference ${it.first} to child \n$child \nResult: $result")
            return result;
        }

        fun checkIfFightCanBePacked(fightId: String, getFight: (id: String) -> FightDescription): Boolean {
            val fight = getFight(fightId)
            return if (fight.scores!!.size >= 2) {
                true
            } else {
                listOfNotNull(fight.parentId1, fight.parentId2).map { it.referenceType to it.fightId?.let { id -> getFight(id) } }.filter { it.first != null && it.second != null }
                        .filter { it.second!!.scores!!.none { sc -> fight.scores!!.any { fsc -> fsc.competitor.id == sc.competitor.id } } }
                        .filter { checkIfFightCanProduceReference(it.second?.id!!, it.first!!, getFight) }
                        .size + (fight.scores?.size ?: 0) >= 2
            }
        }
    }

    fun currentRoundFights(numberOfFightsInCurrentRound: Int, competitionId: String,
                           categoryId: String,
                           stageId: String,
                           currentRound: Int,
                           roundType: StageRoundType,
                           duration: BigDecimal) = (0 until numberOfFightsInCurrentRound).map { index ->
        fightDescription(competitionId,
                categoryId,
                stageId,
                currentRound,
                roundType,
                index,
                duration,
                "Round $currentRound, fight #${index + 1}")
    }


    fun generateEmptyWinnerRoundsForCategory(competitionId: String, categoryId: String, stageId: String, compssize: Int): List<FightDescription> {
        val numberOfRounds = LongMath.log2(compssize.toLong(), RoundingMode.CEILING)
        val duration = calculateDuration(categoryId)
        log.trace("NumberOfRounds: $numberOfRounds")
        tailrec fun createWinnerFightNodes(result: List<FightDescription>, previousRoundFights: List<FightDescription>, currentRound: Int, totalRounds: Int): List<FightDescription> {
            log.trace("Loop: result.size=${result.size}, previousFights.size=${previousRoundFights.size}, currentRound=$currentRound, totalRounds=$totalRounds")
            if (currentRound >= totalRounds) {
                log.trace("currentRound >= totalRounds, Returning result")
                return result
            }
            if (currentRound < 0) {
                log.trace("currentRound < 0, Returning result")
                return result
            }
            val numberOfFightsInCurrentRound = IntMath.pow(2, totalRounds - currentRound - 1)
            val currentRoundFights = currentRoundFights(numberOfFightsInCurrentRound, competitionId, categoryId, stageId, currentRound, StageRoundType.WINNER_BRACKETS, duration)
            if (currentRound == 0) {
                //this is the first round
                log.trace("This is the first round.")
                return if (currentRound == totalRounds - 1) {
                    //this is the final round, it means there's only one fight.
                    log.trace("This is the final round.")
                    currentRoundFights.map { it.copy(fightName = FINAL, roundType = StageRoundType.GRAND_FINAL) }
                } else {
                    log.trace("This is not the final round -> recursion")
                    createWinnerFightNodes(result, currentRoundFights, currentRound + 1, totalRounds)
                }
            } else {
                //we need to assign parent ids to the newly generated fights
                log.trace("This is not the first round.")
                val connectedFights = createConnectedTripletsFrom(previousRoundFights, currentRoundFights) {
                    Tuple3(it.a.copy(winFight = it.c.id), it.b.copy(winFight = it.c.id), it.c.copy(parentId1 = ParentFightReference(FightReferenceType.WINNER, it.a.id), parentId2 = ParentFightReference(FightReferenceType.WINNER, it.b.id)))
                }
                return if (currentRound == totalRounds - 1) {
                    log.trace("This is the final round.")
                    assert(connectedFights.size == 1) { "Connected fights size is not 1 in the last round, but (${connectedFights.size})." }
                    result + connectedFights.flatMap { listOf(it.a.copy(fightName = SEMI_FINAL), it.b.copy(fightName = SEMI_FINAL), it.c.copy(fightName = FINAL, roundType = StageRoundType.GRAND_FINAL)) }
                } else {
                    log.trace("This is not the final round -> recursion")
                    createWinnerFightNodes(result + connectedFights.flatMap { listOf(it.a, it.b) }, connectedFights.map { it.c }, currentRound + 1, totalRounds)
                }
            }
        }
        return createWinnerFightNodes(emptyList(), emptyList(), 0, numberOfRounds)
    }

    fun mergeAll(pairs: List<Pair<FightDescription, FightDescription>>, fightsList: List<FightDescription>): List<Tuple3<FightDescription, FightDescription, FightDescription>> {
        return pairs.zipWith(fightsList) { pair, fightDescription ->
            Tuple3(pair.first, pair.second, fightDescription)
        }
    }

    fun generateDoubleEliminationBracket(competitionId: String, categoryId: String, stageId: String, compssize: Int) =
            generateLoserBracketAndGrandFinalForWinnerBracket(competitionId, categoryId, stageId, generateEmptyWinnerRoundsForCategory(competitionId, categoryId, stageId, compssize), false)

    fun generateLoserBracketAndGrandFinalForWinnerBracket(competitionId: String, categoryId: String, stageId: String, winnerFights: List<FightDescription>, hasLoserGrandFinal: Boolean = false): List<FightDescription> {
        val duration = calculateDuration(categoryId)
        assert(winnerFights.all { it.roundType == StageRoundType.WINNER_BRACKETS }) { "Winner brackets fights contain not winner-brackets round types." }
        assert(winnerFights.none { it.parentId2?.referenceType == FightReferenceType.LOSER }) { "Winner brackets fights contain contain references from loser brackets." }
        val totalWinnerRounds = winnerFights.maxBy { it.round!! }?.round!! + 1
        val grandFinal = fightDescription(competitionId, categoryId, stageId, totalWinnerRounds, StageRoundType.GRAND_FINAL, 0, duration, GRAND_FINAL)
        val totalLoserRounds = 2 * (totalWinnerRounds - 1)
        val firstWinnerRoundFights = winnerFights.filter { it.round == 0 }
        val loserBracketsSize = firstWinnerRoundFights.size / 2
        assert(DoubleMath.isMathematicalInteger(DoubleMath.log2(loserBracketsSize.toDouble()))) { "Loser brackets size should be a power of two, but it is $loserBracketsSize" }


        tailrec fun createLoserFightNodes(result: List<FightDescription>,
                                          previousLoserRoundFights: List<FightDescription>,
                                          winnerFights: List<FightDescription>,
                                          currentLoserRound: Int,
                                          currentWinnerRound: Int): List<FightDescription> {
            log.info("Loop: result.size=${result.size}, previousLoserRoundFights.size=${previousLoserRoundFights.size}, currentLoserRound=$currentLoserRound, currentWinnerRound=$currentWinnerRound, totalWinnerRounds=$totalWinnerRounds, totalLoserRounds:$totalLoserRounds")
            if (totalWinnerRounds <= 0 || totalLoserRounds <= 0) {
                return result
            }
            val numberOfFightsInCurrentRound = if (currentLoserRound % 2 == 0) {
                loserBracketsSize / IntMath.pow(2, currentLoserRound / 2)
            } else {
                previousLoserRoundFights.size
            }
            val currentLoserRoundFights = currentRoundFights(numberOfFightsInCurrentRound, competitionId, categoryId, stageId, currentLoserRound, StageRoundType.LOSER_BRACKETS, duration)
            val connectedFights = if (currentLoserRound == 0) {
                //this is the first loser brackets round
                //we take the first round of the winner brackets and connect them via loserFights to the generated fights
                createConnectedTripletsFrom(firstWinnerRoundFights, currentLoserRoundFights) {
                    Tuple3(it.a.copy(loseFight = it.c.id), it.b.copy(loseFight = it.c.id), it.c.copy(parentId1 = ParentFightReference(FightReferenceType.LOSER, it.a.id), parentId2 = ParentFightReference(FightReferenceType.LOSER, it.b.id)))
                }
            } else {
                if (currentLoserRound % 2 == 0) {
                    //it means there will be no competitors falling from the upper bracket.
                    createConnectedTripletsFrom(previousLoserRoundFights, currentLoserRoundFights) {
                        Tuple3(it.a.copy(winFight = it.c.id), it.b.copy(winFight = it.c.id), it.c.copy(parentId1 = ParentFightReference(FightReferenceType.WINNER, it.a.id), parentId2 = ParentFightReference(FightReferenceType.WINNER, it.b.id)))
                    }
                } else {
                    //we need to merge the winners of fights from the previous loser rounds
                    //and the losers of the fights from the previous winner round
                    val winnerRoundFights = winnerFights.filter { it.round == currentWinnerRound }
                    assert(winnerRoundFights.size == previousLoserRoundFights.size)
                    val allFights = (winnerRoundFights + previousLoserRoundFights).sortedBy { it.numberInRound * 10 + it.roundType?.ordinal!! }
                    createConnectedTripletsFrom(allFights, currentLoserRoundFights) {
                        Tuple3(it.a.copy(loseFight = it.c.id), it.b.copy(winFight = it.c.id), it.c.copy(parentId1 = ParentFightReference(FightReferenceType.LOSER, it.a.id), parentId2 = ParentFightReference(FightReferenceType.WINNER, it.b.id)))
                    }
                }
            }
            return if (currentLoserRound == totalLoserRounds - 1) {
                assert(connectedFights.size == 1) { "Connected fights size is not 1 in the last round, but (${connectedFights.size})." }
                val lastTuple = connectedFights[0]
                val connectedGrandFinal =
                        grandFinal.copy(parentId1 = ParentFightReference(FightReferenceType.WINNER, lastTuple.a.id), parentId2 = ParentFightReference(FightReferenceType.WINNER, lastTuple.c.id))
                result + lastTuple.a.copy(winFight = connectedGrandFinal.id) + lastTuple.b + lastTuple.c.copy(winFight = connectedGrandFinal.id) + connectedGrandFinal
            } else {
                createLoserFightNodes(result + connectedFights.flatMap { listOf(it.a, it.b) },
                        connectedFights.map { it.c },
                        winnerFights,
                        currentLoserRound + 1,
                        currentWinnerRound + ((currentLoserRound + 1) % 2))
            }
        }
        return createLoserFightNodes(emptyList(), emptyList(), winnerFights, 0, 0)
    }

    fun createConnectedTripletsFrom(previousRoundFights: List<FightDescription>, currentRoundFights: List<FightDescription>, connectFun: (tuple: Tuple3<FightDescription, FightDescription, FightDescription>) -> Tuple3<FightDescription, FightDescription, FightDescription>): List<Tuple3<FightDescription, FightDescription, FightDescription>> {
        val firstWinnerRoundFightsOdd = previousRoundFights.filterIndexed { index, _ -> index % 2 == 0 }
        val firstWinnerRoundFightsEven = previousRoundFights.filterIndexed { index, _ -> index % 2 == 1 }
        val firstWinnerRoundFightsPairs = firstWinnerRoundFightsOdd.zip(firstWinnerRoundFightsEven)
        assert(firstWinnerRoundFightsPairs.size == currentRoundFights.size) { "Something is wrong, first winner round should have exactly twice as much fights (${previousRoundFights.size} as the first loser round (${currentRoundFights.size})." }
        return mergeAll(firstWinnerRoundFightsPairs, currentRoundFights).map(connectFun)
    }

    fun filterPreliminaryFights(outputSize: Int, fights: List<FightDescription>, bracketType: BracketType): List<FightDescription> {
        log.info("Filtering fights: $outputSize, fights size: ${fights.size}, brackets type: $bracketType")
        val result = when (bracketType) {
            BracketType.SINGLE_ELIMINATION -> {
                if (outputSize == 3) {
                    val thirdPlaceFight = fights.firstOrNull { it.roundType == StageRoundType.THIRD_PLACE_FIGHT }
                    assert(thirdPlaceFight != null) { "There is no fight for third place, but the output of the stage is 3, cannot calculate." }
                    val grandFinal = fights.firstOrNull { it.roundType == StageRoundType.GRAND_FINAL }
                    fights.filter { it.id != grandFinal?.id }
                } else {
                    assert(DoubleMath.isMathematicalInteger(DoubleMath.log2(outputSize.toDouble()))) { "Output for single elimination brackets must be power of two, but it is $outputSize" }
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

    fun generateThirdPlaceFightForOlympicSystem(competitionId: String, categoryId: String, stageId: String, winnerFights: List<FightDescription>): List<FightDescription> {
        if (winnerFights.isEmpty()) {
            return winnerFights
        }
        assert(winnerFights.all { it.roundType == StageRoundType.WINNER_BRACKETS && it.round != null })
        val semiFinal = winnerFights.fold(0) { acc, fightDescription -> max(fightDescription.round!!, acc) } - 1
        val semiFinalFights = winnerFights.filter { it.round == semiFinal }
        assert(semiFinalFights.size == 2) { "There should be exactly two semifinal fights, but there are ${winnerFights.count { it.round == semiFinal }}" }
        val thirdPlaceFight = fightDescription(competitionId, categoryId, stageId, semiFinal + 1, StageRoundType.THIRD_PLACE_FIGHT, 0, semiFinalFights[0].duration
                ?: calculateDuration(categoryId), THIRD_PLACE_FIGHT)
        val updatedFights = listOf(semiFinalFights[0].copy(loseFight = thirdPlaceFight.id), semiFinalFights[1].copy(loseFight = thirdPlaceFight.id),
                thirdPlaceFight.copy(parentId1 = ParentFightReference(FightReferenceType.LOSER, semiFinalFights[0].id), parentId2 = ParentFightReference(FightReferenceType.LOSER, semiFinalFights[1].id)))
        return winnerFights.map {
            when (it.id) {
                updatedFights[0].id -> updatedFights[0]
                updatedFights[1].id -> updatedFights[1]
                else -> it
            }
        } + updatedFights[2]
    }

    private fun createFightId(competitionId: String, categoryId: String?, stageId: String, round: Int, number: Int, roundType: StageRoundType?) = IDGenerator.fightId(competitionId, categoryId, stageId, round, number, roundType)


    private fun fightDescription(competitionId: String, categoryId: String, stageId: String, round: Int, roundType: StageRoundType, index: Int, duration: BigDecimal, fightName: String?): FightDescription {
        return FightDescription(
                fightId = createFightId(competitionId, categoryId, stageId, round, index, roundType),
                categoryId = categoryId,
                round = round,
                numberInRound = index,
                competitionId = competitionId)
                .apply {
                    this.duration = duration
                    this.roundType = roundType
                    this.fightName = fightName
                }
    }


    private fun calculateDuration(categoryId: String) = categoryCrudRepository.findById(categoryId).map { it.fightDuration }.orElseThrow { CategoryNotFoundException("Category not found for id: $categoryId") }

    fun distributeCompetitors(competitors: List<Competitor>, fights: List<FightDescription>, bracketType: BracketType, distributionType: DistributionType = DistributionType.RANDOM): List<FightDescription> {
        when (bracketType) {
            BracketType.SINGLE_ELIMINATION, BracketType.DOUBLE_ELIMINATION -> {
                val firstRoundFights = fights.filter { it.round == 0 && it.roundType != StageRoundType.LOSER_BRACKETS }
                assert(fights.size * 2 >= competitors.size) { "Number of fights in the first round is ${fights.size}, which is less than required to fit ${competitors.size} competitors." }
                var comps = competitors.shuffled()
                fun getNextCompetitor() = if (comps.nonEmpty()) {
                    val comp = comps.first()
                    comps = comps.drop(1)
                    comp
                } else null

                val updatedFirstRoundFights = firstRoundFights
                        .map { f ->
                            getNextCompetitor()?.let { f.pushCompetitor(it) } ?: f
                        }
                        .map { f ->
                            getNextCompetitor()?.let { f.pushCompetitor(it) } ?: f
                        }
                assert(getNextCompetitor() == null) { "Not all competitors were distributed." }
                return fights.map {
                    val updatedFight = updatedFirstRoundFights.find { uf -> uf.id == it.id }
                    updatedFight ?: it
                }
            }
            else -> {
                log.warn("TODO")
                return fights
            }
        }
    }

    fun filterUncompleteFirstRoundFights(fights: List<FightDescription>): List<FightDescription> {
        val firstRoundFights = fights.filter { it.id != null && !checkIfFightCanBePacked(it.id!!) { id -> fights.first { fight -> fight.id == id } } }
        return firstRoundFights.fold(fights) { acc, fightDescription ->
            val updatedFight = fightDescription.copy(stage = FightStage.FINISHED, fightResult = FightResult(fightDescription.scores?.firstOrNull()?.competitor?.id, CompetitorResultType.WALKOVER, "BYE"))
            val winFightId = fightDescription.winFight
            val updates = if (!winFightId.isNullOrBlank()) {
                //find win fight
                val winfight = acc.first { it.id == winFightId }
                fightDescription.scores?.firstOrNull()?.competitor?.let {
                    listOf(updatedFight, winfight.pushCompetitor(it))
                } ?: listOf(updatedFight)
            } else {
                listOf(updatedFight)
            }
            acc.map { f ->
                val updF = updates.firstOrNull { it.id == f.id }
                updF ?: f
            }
        }
    }

    fun buildStageResults(bracketType: BracketType,
                          stageStatus: StageStatus,
                          fights: List<FightDescription>,
                          stageId: String,
                          competitionId: String): List<CompetitorResult> {
        return when (stageStatus) {
            StageStatus.FINISHED -> {
                when (bracketType) {
                    BracketType.SINGLE_ELIMINATION -> {
                        val grandFinal = fights.first { it.roundType == StageRoundType.GRAND_FINAL }
                        val thirdPlaceFight = fights.firstOrNull { it.roundType == StageRoundType.THIRD_PLACE_FIGHT }
                        val finalRound = grandFinal.round!!
                        val fightsByRounds = thirdPlaceFight?.let { fights.filter { it.roundType != StageRoundType.GRAND_FINAL && it.roundType != StageRoundType.THIRD_PLACE_FIGHT && it.round != finalRound - 1 } }
                                ?: fights.filter { it.roundType != StageRoundType.GRAND_FINAL }
                                ?: emptyList()

                        fun calculateLoserPlace(round: Int): Int {
                            val diff = finalRound - round
                            assert(diff > 0) { "Grand final should be the only fight with the biggest round number." }
                            return diff * 2 + 1
                        }
                        fightsByRounds.mapNotNull { f ->
                            when (f.fightResult?.resultType) {
                                CompetitorResultType.WIN_DECISION, CompetitorResultType.WIN_POINTS, CompetitorResultType.WIN_SUBMISSION, CompetitorResultType.OPPONENT_DQ -> {
                                    f.scores?.find { it.competitor.id != f.fightResult?.winnerId!! }?.let { compScore ->
                                        CompetitorResult(compScore.competitor.id + stageId + competitionId, compScore.competitor, 0, f.round, calculateLoserPlace(f.round!!), null, mutableSetOf())
                                    }
                                }
                                else -> null
                            }
                        } + grandFinal.scores!!.map {
                            val place = if (it.competitor.id == grandFinal.fightResult!!.winnerId) 1 else 2
                            CompetitorResult(it.competitor.id + stageId + competitionId, it.competitor, 0, grandFinal.round, place, null, mutableSetOf())
                        } + (thirdPlaceFight?.scores?.map {
                            val place = if (it.competitor.id == thirdPlaceFight.fightResult!!.winnerId) 3 else 4
                            CompetitorResult(it.competitor.id + stageId + competitionId, it.competitor, 0, thirdPlaceFight.round, place, null, mutableSetOf())
                        } ?: emptyList())
                    }
                    BracketType.DOUBLE_ELIMINATION -> {
                        emptyList()
                    }
                    else -> TODO()
                }
            }
            else -> emptyList()
        }
    }



    fun applyStageInputDescriptorToResultsAndFights(descriptor: StageInputDescriptor,
                                                    results: List<CompetitorResult>,
                                                    fights: List<FightDescription>): List<Competitor> {
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

        fun findWinnersOrLosers(selector: CompetitorSelector, selectorFun: (fightId: String) -> String?, results: List<CompetitorResult>): List<CompetitorResult> {
            val selectorVal = selector.selectorValue!!
            val selectedFighterIds = selectorVal.mapNotNull { selectorFun.invoke(it) }
            return results.filter { selectedFighterIds.contains(it.competitor?.id) }
        }

        fun filterResults(selector: CompetitorSelector, results: List<CompetitorResult>): List<CompetitorResult> {
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
        return filterResults(descriptor.selectors!!.first(), results).mapNotNull { it.competitor }.take(descriptor.numberOfCompetitors)
    }
}