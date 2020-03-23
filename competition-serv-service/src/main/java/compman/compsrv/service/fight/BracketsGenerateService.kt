package compman.compsrv.service.fight

import arrow.core.Tuple3
import arrow.core.extensions.list.foldable.nonEmpty
import arrow.core.extensions.list.zip.zipWith
import com.google.common.math.DoubleMath
import com.google.common.math.IntMath
import com.google.common.math.LongMath
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightResultDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.util.copy
import compman.compsrv.util.pushCompetitor
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

@Component
class BracketsGenerateService : FightsService() {

    override fun supportedBracketTypes(): List<BracketType> = listOf(BracketType.SINGLE_ELIMINATION,
            BracketType.DOUBLE_ELIMINATION)

    private fun currentRoundFights(numberOfFightsInCurrentRound: Int, competitionId: String,
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
                "Round $currentRound, fight #${index + 1}", null)
    }


    fun generateEmptyWinnerRoundsForCategory(competitionId: String, categoryId: String, stageId: String, compssize: Int, duration: BigDecimal): List<FightDescriptionDTO> {
        val numberOfRounds = LongMath.log2(compssize.toLong(), RoundingMode.CEILING)
        log.trace("NumberOfRounds: $numberOfRounds")
        tailrec fun createWinnerFightNodes(result: List<FightDescriptionDTO>, previousRoundFights: List<FightDescriptionDTO>, currentRound: Int, totalRounds: Int): List<FightDescriptionDTO> {
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
                    Tuple3(it.a.copy(winFight = it.c.id), it.b.copy(winFight = it.c.id), it.c.copy(parentId1 = ParentFightReferenceDTO(FightReferenceType.WINNER, it.a.id), parentId2 = ParentFightReferenceDTO(FightReferenceType.WINNER, it.b.id)))
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

    fun mergeAll(pairs: List<Pair<FightDescriptionDTO, FightDescriptionDTO>>, fightsList: List<FightDescriptionDTO>): List<Tuple3<FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO>> {
        return pairs.zipWith(fightsList) { pair, fightDescription ->
            Tuple3(pair.first, pair.second, fightDescription)
        }
    }

    fun generateDoubleEliminationBracket(competitionId: String, categoryId: String, stageId: String, compssize: Int, duration: BigDecimal) =
            generateLoserBracketAndGrandFinalForWinnerBracket(competitionId, categoryId, stageId, generateEmptyWinnerRoundsForCategory(competitionId, categoryId, stageId, compssize, duration), duration, false)

    fun generateLoserBracketAndGrandFinalForWinnerBracket(competitionId: String, categoryId: String, stageId: String, winnerFightsAndGrandFinal: List<FightDescriptionDTO>, duration: BigDecimal, hasLoserGrandFinal: Boolean = false): List<FightDescriptionDTO> {
        assert(winnerFightsAndGrandFinal.count { it.roundType == StageRoundType.GRAND_FINAL && it.round != null } == 1)
        assert(winnerFightsAndGrandFinal.filter { it.roundType != StageRoundType.GRAND_FINAL }.all { it.roundType == StageRoundType.WINNER_BRACKETS && it.round != null }) { "Winner brackets fights contain not winner-brackets round types." }
        assert(winnerFightsAndGrandFinal.none { it.parentId2?.referenceType == FightReferenceType.LOSER }) { "Winner brackets fights contain contain references from loser brackets." }
        val winnerFights = winnerFightsAndGrandFinal.filter { it.roundType != StageRoundType.GRAND_FINAL } + winnerFightsAndGrandFinal.first { it.roundType == StageRoundType.GRAND_FINAL }.copy(roundType = StageRoundType.WINNER_BRACKETS)
        val totalWinnerRounds = winnerFights.maxBy { it.round!! }?.round!! + 1
        val grandFinal = fightDescription(competitionId, categoryId, stageId, totalWinnerRounds, StageRoundType.GRAND_FINAL, 0, duration, GRAND_FINAL, null)
        val totalLoserRounds = 2 * (totalWinnerRounds - 1)
        val firstWinnerRoundFights = winnerFights.filter { it.round == 0 }
        val loserBracketsSize = firstWinnerRoundFights.size / 2
        assert(DoubleMath.isMathematicalInteger(DoubleMath.log2(loserBracketsSize.toDouble()))) { "Loser brackets size should be a power of two, but it is $loserBracketsSize" }


        tailrec fun createLoserFightNodes(result: List<FightDescriptionDTO>,
                                          previousLoserRoundFights: List<FightDescriptionDTO>,
                                          winnerFights: List<FightDescriptionDTO>,
                                          currentLoserRound: Int,
                                          currentWinnerRound: Int): List<FightDescriptionDTO> {
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
                    Tuple3(it.a.copy(loseFight = it.c.id), it.b.copy(loseFight = it.c.id), it.c.copy(parentId1 = ParentFightReferenceDTO(FightReferenceType.LOSER, it.a.id), parentId2 = ParentFightReferenceDTO(FightReferenceType.LOSER, it.b.id)))
                }
            } else {
                if (currentLoserRound % 2 == 0) {
                    //it means there will be no competitors falling from the upper bracket.
                    createConnectedTripletsFrom(previousLoserRoundFights, currentLoserRoundFights) {
                        Tuple3(it.a.copy(winFight = it.c.id), it.b.copy(winFight = it.c.id), it.c.copy(parentId1 = ParentFightReferenceDTO(FightReferenceType.WINNER, it.a.id), parentId2 = ParentFightReferenceDTO(FightReferenceType.WINNER, it.b.id)))
                    }
                } else {
                    //we need to merge the winners of fights from the previous loser rounds
                    //and the losers of the fights from the previous winner round
                    val winnerRoundFights = winnerFights.filter { it.round == currentWinnerRound }
                    assert(winnerRoundFights.size == previousLoserRoundFights.size)
                    val allFights = (winnerRoundFights + previousLoserRoundFights).sortedBy { it.numberInRound * 10 + it.roundType?.ordinal!! }
                    createConnectedTripletsFrom(allFights, currentLoserRoundFights) {
                        Tuple3(it.a.copy(loseFight = it.c.id), it.b.copy(winFight = it.c.id), it.c.copy(parentId1 = ParentFightReferenceDTO(FightReferenceType.LOSER, it.a.id), parentId2 = ParentFightReferenceDTO(FightReferenceType.WINNER, it.b.id)))
                    }
                }
            }
            return if (currentLoserRound == totalLoserRounds - 1) {
                assert(connectedFights.size == 1) { "Connected fights size is not 1 in the last round, but (${connectedFights.size})." }
                val lastTuple = connectedFights[0]
                val connectedGrandFinal =
                        grandFinal.copy(parentId1 = ParentFightReferenceDTO(FightReferenceType.WINNER, lastTuple.a.id), parentId2 = ParentFightReferenceDTO(FightReferenceType.WINNER, lastTuple.c.id))
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

    fun createConnectedTripletsFrom(previousRoundFights: List<FightDescriptionDTO>, currentRoundFights: List<FightDescriptionDTO>, connectFun: (tuple: Tuple3<FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO>) -> Tuple3<FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO>): List<Tuple3<FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO>> {
        val firstWinnerRoundFightsOdd = previousRoundFights.filterIndexed { index, _ -> index % 2 == 0 }
        val firstWinnerRoundFightsEven = previousRoundFights.filterIndexed { index, _ -> index % 2 == 1 }
        val firstWinnerRoundFightsPairs = firstWinnerRoundFightsOdd.zip(firstWinnerRoundFightsEven)
        assert(firstWinnerRoundFightsPairs.size == currentRoundFights.size) { "Something is wrong, first winner round should have exactly twice as much fights (${previousRoundFights.size} as the first loser round (${currentRoundFights.size})." }
        return mergeAll(firstWinnerRoundFightsPairs, currentRoundFights).map(connectFun)
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

    fun generateThirdPlaceFightForOlympicSystem(competitionId: String, categoryId: String, stageId: String, winnerFights: List<FightDescriptionDTO>): List<FightDescriptionDTO> {
        if (winnerFights.isEmpty()) {
            return winnerFights
        }
        assert(winnerFights.count { it.roundType == StageRoundType.GRAND_FINAL && it.round != null } == 1)
        assert(winnerFights.filter { it.roundType != StageRoundType.GRAND_FINAL }.all { it.roundType == StageRoundType.WINNER_BRACKETS && it.round != null })
        val semiFinal = winnerFights.fold(0) { acc, fightDescription -> max(fightDescription.round!!, acc) } - 1
        val semiFinalFights = winnerFights.filter { it.round == semiFinal }
        assert(semiFinalFights.size == 2) { "There should be exactly two semifinal fights, but there are ${winnerFights.count { it.round == semiFinal }}" }
        val thirdPlaceFight = fightDescription(competitionId, categoryId, stageId, semiFinal + 1, StageRoundType.THIRD_PLACE_FIGHT, 0, semiFinalFights[0].duration!!, THIRD_PLACE_FIGHT, null)
        val updatedFights = listOf(semiFinalFights[0].copy(loseFight = thirdPlaceFight.id), semiFinalFights[1].copy(loseFight = thirdPlaceFight.id),
                thirdPlaceFight.copy(parentId1 = ParentFightReferenceDTO(FightReferenceType.LOSER, semiFinalFights[0].id), parentId2 = ParentFightReferenceDTO(FightReferenceType.LOSER, semiFinalFights[1].id)))
        return winnerFights.map {
            when (it.id) {
                updatedFights[0].id -> updatedFights[0]
                updatedFights[1].id -> updatedFights[1]
                else -> it
            }
        } + updatedFights[2]
    }

    override fun generateStageFights(competitionId: String,
                                     categoryId: String,
                                     stage: StageDescriptorDTO,
                                     compssize: Int,
                                     duration: BigDecimal,
                                     competitors: List<CompetitorDTO>,
                                     outputSize: Int): List<FightDescriptionDTO> {
        val fights = when (stage.bracketType) {
            BracketType.SINGLE_ELIMINATION -> {
                if (stage.hasThirdPlaceFight == true) {
                    generateThirdPlaceFightForOlympicSystem(competitionId, categoryId, stage.id,
                            generateEmptyWinnerRoundsForCategory(competitionId, categoryId, stage.id, compssize, duration))
                } else {
                    generateEmptyWinnerRoundsForCategory(competitionId, categoryId, stage.id, compssize, duration)
                }
            }
            BracketType.DOUBLE_ELIMINATION -> generateDoubleEliminationBracket(competitionId, categoryId, stage.id, compssize, duration)
            else -> throw IllegalArgumentException("Bracket type ${stage.bracketType} is not supported.")
        }

        val assignedFights = when (stage.stageOrder) {
            0 -> {
                distributeCompetitors(competitors, fights, stage.bracketType)
            }
            else -> {
                fights
            }
        }

        val filteredUncompletableFights = filterUncompletableFirstRoundFights(assignedFights) { id ->
            assignedFights.first { fight -> fight.id == id }
        }


        val processedFights = if (stage.stageType == StageType.PRELIMINARY) {
            filterPreliminaryFights(outputSize, filteredUncompletableFights, stage.bracketType)
        } else {
            filteredUncompletableFights
        }

        return processedFights
    }


    override fun distributeCompetitors(competitors: List<CompetitorDTO>, fights: List<FightDescriptionDTO>, bracketType: BracketType, distributionType: DistributionType): List<FightDescriptionDTO> {
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
                TODO()
            }
        }
    }

    fun filterUncompletableFirstRoundFights(fights: List<FightDescriptionDTO>, getFightById: (id: String) -> FightDescriptionDTO): List<FightDescriptionDTO> {
        val firstRoundFights = fights.filter { it.id != null && !checkIfFightCanBePacked(it.id!!, getFightById) }
        return firstRoundFights.fold(fights) { acc, fightDescription ->
            val updatedFight = fightDescription.copy(status = FightStatus.UNCOMPLETABLE, fightResult = FightResultDTO(fightDescription.scores?.firstOrNull()?.competitor?.id, null, "BYE"))
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

    override fun buildStageResults(bracketType: BracketType,
                                   stageStatus: StageStatus,
                                   fights: List<FightDescriptionDTO>,
                                   stageId: String,
                                   competitionId: String,
                                   pointsAssignmentDescriptors: List<FightResultOptionDTO>): List<CompetitorStageResultDTO> {
        return when (stageStatus) {
            StageStatus.FINISHED -> {
                when (bracketType) {
                    BracketType.SINGLE_ELIMINATION -> {
                        val grandFinal = fights.first { it.roundType == StageRoundType.GRAND_FINAL }
                        val thirdPlaceFight = fights.firstOrNull { it.roundType == StageRoundType.THIRD_PLACE_FIGHT }
                        val finalRound = grandFinal.round!!
                        val filteredFights = thirdPlaceFight?.let { fights.filter { it.roundType != StageRoundType.GRAND_FINAL && it.roundType != StageRoundType.THIRD_PLACE_FIGHT && it.round != finalRound - 1 } }
                                ?: fights.filter { it.roundType != StageRoundType.GRAND_FINAL }
                               .orEmpty()

                        fun calculateLoserPlace(round: Int): Int {
                            val diff = finalRound - round
                            assert(diff > 0) { "Grand final should be the only fight with the biggest round number." }
                            return diff * 2 + 1
                        }
                        filteredFights.mapNotNull { f ->
                            log.info("Processing fight for result: $f")
                            val result = f.scores?.find { it.competitor.id != f.fightResult?.winnerId!! }?.let { compScore ->
                                CompetitorStageResultDTO()
                                        .setStageId(stageId)
                                        .setCompetitorId(compScore.competitor.id)
                                        .setPoints(BigDecimal.ZERO)
                                        .setRound(f.round)
                                        .setRoundType(f.roundType)
                                        .setPlace(calculateLoserPlace(f.round!!))
                            }
                            log.info("Created competitor result: $result")
                            result
                        } + grandFinal.scores!!.map {
                            val place = if (it.competitor.id == grandFinal.fightResult!!.winnerId) 1 else 2
                            CompetitorStageResultDTO()
                                    .setStageId(stageId)
                                    .setCompetitorId(it.competitor.id)
                                    .setPoints(BigDecimal.ZERO)
                                    .setRound(grandFinal.round)
                                    .setRoundType(grandFinal.roundType)
                                    .setPlace(place)
                        } + (thirdPlaceFight?.scores?.map {
                            val place = if (it.competitor.id == thirdPlaceFight.fightResult!!.winnerId) 3 else 4
                            CompetitorStageResultDTO()
                                    .setStageId(stageId)
                                    .setCompetitorId(it.competitor.id)
                                    .setPoints(BigDecimal.ZERO)
                                    .setRound(thirdPlaceFight.round)
                                    .setRoundType(thirdPlaceFight.roundType)
                                    .setPlace(place)
                        }.orEmpty())
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


}