package compman.compsrv.service.fight

import arrow.core.Tuple3
import arrow.core.extensions.list.foldable.nonEmpty
import arrow.core.extensions.list.zip.zipWith
import com.google.common.math.DoubleMath
import com.google.common.math.IntMath
import com.google.common.math.LongMath
import compman.compsrv.mapping.toPojo
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
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
                "Round ${currentRound + 1}, fight #${index + 1}", null)
    }

    fun getPriority(roundType: StageRoundType?) = when (roundType) {
        StageRoundType.GRAND_FINAL -> 0
        StageRoundType.THIRD_PLACE_FIGHT -> 1
        StageRoundType.WINNER_BRACKETS -> 2
        StageRoundType.LOSER_BRACKETS -> 3
        StageRoundType.GROUP -> 4
        else -> Int.MAX_VALUE
    }

    fun createScores(ids: List<String>, refTypes: List<FightReferenceType>): Array<CompScoreDTO> {
        assert(ids.size == refTypes.size || refTypes.size == 1) { "The sizes of ids and refTypes should match, or there should be exactly one refType." }
        return if (ids.size == refTypes.size) {
            ids.zip(refTypes).mapIndexed { i, p ->
                CompScoreDTO().setParentReferenceType(p.second)
                        .setParentFightId(p.first).setOrder(i)
            }.toTypedArray()
        } else {
            ids.mapIndexed { i, p ->
                CompScoreDTO().setParentReferenceType(refTypes.first())
                        .setParentFightId(p).setOrder(i)
            }.toTypedArray()
        }
    }

    val connectWinWin = { it: Tuple3<FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO> ->
        Tuple3(it.a.copy(winFight = it.c.id), it.b.copy(winFight = it.c.id),
                it.c.copy(scores = createScores(listOf(it.a.id, it.b.id), listOf(FightReferenceType.WINNER))))
    }

    val connectLoseLose = { it: Tuple3<FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO> ->
        Tuple3(it.a.copy(loseFight = it.c.id), it.b.copy(loseFight = it.c.id),
                it.c.copy(scores = createScores(listOf(it.a.id, it.b.id), listOf(FightReferenceType.LOSER))))
    }
    val connectLoseWin = { it: Tuple3<FightDescriptionDTO, FightDescriptionDTO, FightDescriptionDTO> ->
        Tuple3(it.a.copy(loseFight = it.c.id), it.b.copy(winFight = it.c.id),
                it.c.copy(scores = createScores(listOf(it.a.id, it.b.id), listOf(FightReferenceType.LOSER, FightReferenceType.WINNER))))
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
                val connectedFights = createConnectedTripletsFrom(previousRoundFights, currentRoundFights, connectWinWin)
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
        assert(winnerFightsAndGrandFinal.none { it.scores?.any { dto -> dto.parentReferenceType == FightReferenceType.LOSER } == true }) { "Winner brackets fights contain contain references from loser brackets." }
        val winnerFights = winnerFightsAndGrandFinal.filter { it.roundType != StageRoundType.GRAND_FINAL } +
                winnerFightsAndGrandFinal.first { it.roundType == StageRoundType.GRAND_FINAL }.copy(roundType = StageRoundType.WINNER_BRACKETS)
        val totalWinnerRounds = getMaxRound(winnerFights) + 1
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
                createConnectedTripletsFrom(firstWinnerRoundFights, currentLoserRoundFights, connectLoseLose)
            } else {
                if (currentLoserRound % 2 == 0) {
                    //it means there will be no competitors falling from the upper bracket.
                    createConnectedTripletsFrom(previousLoserRoundFights, currentLoserRoundFights, connectWinWin)
                } else {
                    //we need to merge the winners of fights from the previous loser rounds
                    //and the losers of the fights from the previous winner round
                    val winnerRoundFights = winnerFights.filter { it.round == currentWinnerRound }
                    assert(winnerRoundFights.size == previousLoserRoundFights.size)
                    val allFights = (winnerRoundFights + previousLoserRoundFights).sortedBy { it.numberInRound * 10 + getPriority(it.roundType) }
                    createConnectedTripletsFrom(allFights, currentLoserRoundFights, connectLoseWin)
                }
            }
            return if (currentLoserRound == totalLoserRounds - 1) {
                assert(connectedFights.size == 1) { "Connected fights size is not 1 in the last round, but (${connectedFights.size})." }
                val lastTuple = connectedFights[0]
                val connectedGrandFinal =
                        grandFinal.copy(scores = createScores(listOf(lastTuple.a.id, lastTuple.c.id), listOf(FightReferenceType.WINNER)))
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
        val previousRoundFightsOdd = previousRoundFights.filterIndexed { index, _ -> index % 2 == 0 }
        val previousRoundFightsEven = previousRoundFights.filterIndexed { index, _ -> index % 2 == 1 }
        val previousRoundFightsInPairs = previousRoundFightsOdd.zip(previousRoundFightsEven)
        assert(previousRoundFightsInPairs.size == currentRoundFights.size) { "Something is wrong, previous round should have exactly twice as much fights (${previousRoundFights.size}) as the connected round (${currentRoundFights.size})." }
        return mergeAll(previousRoundFightsInPairs, currentRoundFights).map(connectFun)
    }

    fun generateThirdPlaceFightForOlympicSystem(competitionId: String, categoryId: String, stageId: String, winnerFights: List<FightDescriptionDTO>): List<FightDescriptionDTO> {
        if (winnerFights.isEmpty()) {
            return winnerFights
        }
        assert(winnerFights.count { it.roundType == StageRoundType.GRAND_FINAL && it.round != null } == 1)
        assert(winnerFights.filter { it.roundType != StageRoundType.GRAND_FINAL }.all { it.roundType == StageRoundType.WINNER_BRACKETS && it.round != null })
        val semiFinal = getMaxRound(winnerFights) - 1
        val semiFinalFights = winnerFights.filter { it.round == semiFinal }
        assert(semiFinalFights.size == 2) { "There should be exactly two semifinal fights, but there are ${winnerFights.count { it.round == semiFinal }}" }
        val thirdPlaceFight = fightDescription(competitionId, categoryId, stageId, semiFinal + 1, StageRoundType.THIRD_PLACE_FIGHT, 0, semiFinalFights[0].duration!!, THIRD_PLACE_FIGHT, null)
        val updatedFights = listOf(semiFinalFights[0].copy(loseFight = thirdPlaceFight.id), semiFinalFights[1].copy(loseFight = thirdPlaceFight.id),
                thirdPlaceFight.copy(scores = createScores(semiFinalFights.map { f -> f.id }, listOf(FightReferenceType.LOSER))))
        return winnerFights.map {
            when (it.id) {
                updatedFights[0].id -> updatedFights[0]
                updatedFights[1].id -> updatedFights[1]
                else -> it
            }
        } + updatedFights[2]
    }

    private fun getMaxRound(fights: List<FightDescriptionDTO>) =
            fights.fold(0) { acc, fightDescription -> max(fightDescription.round!!, acc) }

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

        val markedUncompletableFights = markAndProcessUncompletableFights(assignedFights, stage.stageStatus) { id ->
            assignedFights.first { fight -> fight.id == id }.scores?.map { it.toPojo(id) }
        }


        return if (stage.stageType == StageType.PRELIMINARY) {
            filterPreliminaryFights(outputSize, markedUncompletableFights, stage.bracketType)
        } else {
            markedUncompletableFights
        }
    }


    override fun distributeCompetitors(competitors: List<CompetitorDTO>, fights: List<FightDescriptionDTO>, bracketType: BracketType): List<FightDescriptionDTO> {
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
                            getNextCompetitor()?.let { f.pushCompetitor(it.id) } ?: f
                        }
                        .map { f ->
                            getNextCompetitor()?.let { f.pushCompetitor(it.id) } ?: f
                        }
                assert(getNextCompetitor() == null) { "Not all competitors were distributed." }
                return fights.map {
                    val updatedFight = updatedFirstRoundFights.find { uf -> uf.id == it.id }
                    updatedFight ?: it
                }
            }
            else -> {
                TODO("Unsupported brackets type $bracketType")
            }
        }
    }

    override fun buildStageResults(bracketType: BracketType,
                                   stageStatus: StageStatus,
                                   stageType: StageType,
                                   fights: List<FightDescriptionDTO>,
                                   stageId: String,
                                   competitionId: String,
                                   fightResultOptions: List<FightResultOptionDTO>): List<CompetitorStageResultDTO> {
        return when (stageStatus) {
            StageStatus.FINISHED -> {
                when (bracketType) {
                    BracketType.SINGLE_ELIMINATION -> {
                        when (stageType) {
                            StageType.PRELIMINARY -> {
                                generatePreliminarySingleElimination(fights, stageId)
                            }
                            StageType.FINAL -> {
                                generateFinalSingleElimination(fights, stageId)
                            }
                        }
                    }
                    BracketType.DOUBLE_ELIMINATION -> {
                        when (stageType) {
                            StageType.PRELIMINARY -> {
                                log.error("Preliminary double elimination is not supported. Returning all the competitors.")
                                generateMockResults(fights, stageId)

                            }
                            StageType.FINAL -> {
                                generateFinalDoubleElimination(fights, stageId)
                            }
                        }
                    }
                    else -> {
                        log.error("$bracketType is not supported. Returning all the competitors.")
                        generateMockResults(fights, stageId)
                    }
                }
            }
            else -> emptyList()
        }
    }

    private fun generateMockResults(fights: List<FightDescriptionDTO>, stageId: String): List<CompetitorStageResultDTO> {
        val competitors = getCompetitorsSetFromFights(fights)
        return competitors.mapIndexed { index, cid ->
            mockCompetitorStageResult(stageId, cid, index)
        }
    }

    private fun mockCompetitorStageResult(stageId: String, competitorId: String, place: Int): CompetitorStageResultDTO {
        return CompetitorStageResultDTO()
                .setStageId(stageId)
                .setCompetitorId(competitorId)
                .setPoints(BigDecimal.ZERO)
                .setRound(0)
                .setRoundType(StageRoundType.WINNER_BRACKETS)
                .setPlace(place)
    }

    private fun generateFinalDoubleElimination(fights: List<FightDescriptionDTO>, stageId: String): List<CompetitorStageResultDTO> {
        val finalRoundFight = fights.find { it.roundType == StageRoundType.GRAND_FINAL } ?: error("Could not find the grand final.")
        val finalists = listOfNotNull(
                getWinnerId(finalRoundFight)?.let { wid ->
                    competitorStageResult(stageId, wid, finalRoundFight, 1)
                },
                getLoserId(finalRoundFight)?.let { lid ->
                    competitorStageResult(stageId, lid, finalRoundFight, 2)
                }
        )
        val competitorIds = getCompetitorsSetFromFights(fights) - finalists.mapNotNull { it.competitorId }
        val loserFights = fights.filter { it.roundType == StageRoundType.LOSER_BRACKETS }
        val finalLoserRound = getMaxRound(loserFights)
        val others = competitorIds.map { cid ->
            val lastLostRoundFight = getLastRoundFightForCompetitor(fights, cid)
            val place = finalLoserRound - lastLostRoundFight.round
            competitorStageResult(stageId, cid, lastLostRoundFight, place)
        }
        val placesChunks = others.groupBy { it.place }.toList().sortedBy { it.first }

        return finalists + placesChunks.flatMap {
            val place = placesChunks.filter { pc -> pc.first < it.first }.fold(0) { acc, p -> acc + p.second.size }
            it.second.map { c -> c.setPlace(place + 3) } }
    }

    private fun competitorStageResult(stageId: String, competitorId: String, fight: FightDescriptionDTO, place: Int): CompetitorStageResultDTO {
        return CompetitorStageResultDTO()
                .setStageId(stageId)
                .setCompetitorId(competitorId)
                .setPoints(BigDecimal.ZERO)
                .setRound(fight.round)
                .setRoundType(fight.roundType)
                .setPlace(place)
    }

    fun calculateLoserPlaceForFinalStage(round: Int, finalRound: Int): Int {
        val diff = finalRound - round
        return diff * 2 + 1
    }

    private fun generateFinalSingleElimination(fights: List<FightDescriptionDTO>, stageId: String): List<CompetitorStageResultDTO> {
        val grandFinal = fights.find { it.roundType == StageRoundType.GRAND_FINAL }
                ?: error("The stage is a final stage but has no grand final.")
        val thirdPlaceFight = fights.find { it.roundType == StageRoundType.THIRD_PLACE_FIGHT }
        val grandFinalAndThirdPlace = getCompetitorsSetFromFights(listOfNotNull(grandFinal, thirdPlaceFight))

        val competitors = getCompetitorsSetFromFights(fights) - grandFinalAndThirdPlace

        return grandFinal.scores!!.map {
            val place = if (it.competitorId == grandFinal.fightResult!!.winnerId) 1 else 2
            CompetitorStageResultDTO()
                    .setStageId(stageId)
                    .setCompetitorId(it.competitorId)
                    .setPoints(BigDecimal.ZERO)
                    .setRound(grandFinal.round)
                    .setRoundType(grandFinal.roundType)
                    .setPlace(place)
        } +
                thirdPlaceFight?.scores?.map {
                    val place = if (it.competitorId == thirdPlaceFight.fightResult?.winnerId) 3 else 4
                    CompetitorStageResultDTO()
                            .setStageId(stageId)
                            .setCompetitorId(it.competitorId)
                            .setPoints(BigDecimal.ZERO)
                            .setRound(thirdPlaceFight.round)
                            .setRoundType(thirdPlaceFight.roundType)
                            .setPlace(place)
                }.orEmpty() +
                competitors.map { cid ->
                    val lastFight = getLastRoundFightForCompetitor(fights, cid)
                    competitorStageResult(stageId, cid, lastFight, calculateLoserPlaceForFinalStage(lastFight.round, grandFinal.round))
                }
    }

    private fun generatePreliminarySingleElimination(fights: List<FightDescriptionDTO>, stageId: String): List<CompetitorStageResultDTO> {
        val competitorIds = getCompetitorsSetFromFights(fights)
        val lastRound = getMaxRound(fights)
        val lastRoundFights = fights.filter { it.round == lastRound }
        val lastRoundWinners = lastRoundFights.mapNotNull(Companion::getWinnerId).toSet()
        val lastRoundLosers = lastRoundFights
                .mapNotNull(Companion::getLoserId).toSet()

        fun calculateLoserPlace(round: Int): Int {
            val diff = lastRound - round
            return diff * lastRoundFights.size + 1
        }
        return lastRoundWinners.map { cid ->
            CompetitorStageResultDTO()
                    .setStageId(stageId)
                    .setCompetitorId(cid)
                    .setPoints(BigDecimal.ZERO)
                    .setRound(lastRound)
                    .setRoundType(lastRoundFights.firstOrNull()?.roundType
                            ?: StageRoundType.WINNER_BRACKETS)
                    .setPlace(1)
        } +
                lastRoundLosers.map { cid ->
                    CompetitorStageResultDTO()
                            .setStageId(stageId)
                            .setCompetitorId(cid)
                            .setPoints(BigDecimal.ZERO)
                            .setRound(lastRound)
                            .setRoundType(lastRoundFights.firstOrNull()?.roundType
                                    ?: StageRoundType.WINNER_BRACKETS)
                            .setPlace(2)
                } +
                competitorIds.filter { !lastRoundWinners.contains(it) && !lastRoundLosers.contains(it) }.map { cid ->
                    val lastLostRoundFight = getLastRoundFightForCompetitor(fights, cid)
                    CompetitorStageResultDTO()
                            .setStageId(stageId)
                            .setCompetitorId(cid)
                            .setPoints(BigDecimal.ZERO)
                            .setRound(lastLostRoundFight.round)
                            .setRoundType(lastLostRoundFight.roundType)
                            .setPlace(calculateLoserPlace(lastLostRoundFight.round!!))
                }
    }

    private fun getLastRoundFightForCompetitor(fights: List<FightDescriptionDTO>, cid: String?): FightDescriptionDTO {
        return (fights.filter { f -> f.scores?.any { it.competitorId == cid } == true && f.fightResult?.winnerId != cid }.maxBy { it.round }
                ?: fights.filter { f -> f.status == FightStatus.UNCOMPLETABLE && f.scores?.any { it.competitorId == cid } == true }.maxBy { it.round }
                ?: error("Cannot find the last round fight for competitor $cid"))
    }

    private fun getCompetitorsSetFromFights(fights: List<FightDescriptionDTO>) =
            fights.flatMap { it.scores?.mapNotNull { score -> score.competitorId }.orEmpty() }.toSet()


}