package compman.compsrv.service

import arrow.core.extensions.list.foldable.exists
import compman.compsrv.mapping.toPojo
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.service.fight.BracketsGenerateService
import compman.compsrv.service.fight.FightsService
import compman.compsrv.util.applyConditionalUpdate
import compman.compsrv.util.pushCompetitor
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@RunWith(MockitoJUnitRunner::class)
class BracketsGenerateServiceTest : AbstractGenerateServiceTest() {
    private val fightsGenerateService = BracketsGenerateService()

    private val log = LoggerFactory.getLogger(BracketsGenerateServiceTest::class.java)

    private fun generateEmptyWinnerFights(compsSize: Int): List<FightDescriptionDTO> {
        val fights = fightsGenerateService.generateEmptyWinnerRoundsForCategory(competitionId, categoryId, stageId, compsSize, duration)
        assertNotNull(fights)
        return fights
    }


    @Test
    fun testGenerateEmptyWinnerFights() {
        val fights = generateEmptyWinnerFights(40)
        assertEquals(32 + 16 + 8 + 4 + 2 + 1, fights.size)
        assertEquals(32, fights.filter { it.round == 0 }.size)
        assertEquals(16, fights.filter { it.round == 1 }.size)
        assertEquals(8, fights.filter { it.round == 2 }.size)
        assertEquals(4, fights.filter { it.round == 3 }.size)
        assertEquals(2, fights.filter { it.round == 4 }.size)
        assertEquals(1, fights.filter { it.round == 5 }.size)
        assertTrue(fights.filter { it.round == 5 }.all { it.roundType == StageRoundType.GRAND_FINAL })
        assertTrue { fights.filter { it.round == 0 }.none { f -> f.scores?.any { it.parentFightId != null } == true } }
        assertTrue { fights.filter { it.round != 0 }.none { f -> f.scores?.any { it.parentFightId == null } == true } }
        assertTrue { fights.filter { it.round == 5 }.none { it.winFight != null } }
        assertTrue { fights.filter { it.round != 5 }.none { it.winFight == null } }
        checkWinnerFightsLaws(fights, 32)
    }

    @Test
    fun testGenerateEmptyDoubleEliminationFights() {
        val fights = generateEmptyWinnerFights(14)
        checkWinnerFightsLaws(fights, 8)
        val doubleEliminationBracketFights = fightsGenerateService.generateLoserBracketAndGrandFinalForWinnerBracket(competitionId, categoryId, stageId, fights, duration, true)
        log.info("${doubleEliminationBracketFights.size}")
        log.info(doubleEliminationBracketFights.joinToString("\n"))
        checkDoubleEliminationLaws(doubleEliminationBracketFights, 8)

    }

    @Test
    fun testGenerateThirdPlaceFight() {
        val fights = generateEmptyWinnerFights(14)
        checkWinnerFightsLaws(fights, 8)
        val withThirdPlaceFight = fightsGenerateService.generateThirdPlaceFightForOlympicSystem(competitionId, categoryId, stageId, fights)
        log.info("${withThirdPlaceFight.size}")
        log.info(withThirdPlaceFight.joinToString("\n"))
        assertEquals(fights.size + 1, withThirdPlaceFight.size)
        assertTrue { withThirdPlaceFight.count { it.roundType == StageRoundType.THIRD_PLACE_FIGHT } == 1 }
    }

    @Test
    fun testDistributeCompetitorsWinnerFights() {
        val fights = generateEmptyWinnerFights(14)
        val competitors = FightsService.generateRandomCompetitorsForCategory(14, 20, category, competitionId)
        fightsGenerateService.distributeCompetitors(competitors, fights, BracketType.SINGLE_ELIMINATION)
    }

    private fun printFights(distr: List<FightDescriptionDTO>) {
        log.info(distr.joinToString("\n") { "${it.roundType}-${it.round}-${it.numberInRound}-${it.status}-[${it.scores.joinToString { s -> s.competitorId ?: "_" }}]" })
    }

    @Test
    fun testDistributeCompetitorsDoubleElimination() {
        val marked = createDistributedDoubleElimination(14)
        printFights(marked)
        marked.forEach { markedFight ->
            if (markedFight.status == FightStatus.UNCOMPLETABLE && markedFight.scores?.any { s -> !s.competitorId.isNullOrBlank() } == true && !markedFight.winFight.isNullOrBlank()) {
                assertTrue(marked.find { f -> f.id == markedFight.winFight }?.scores
                        ?.any { s ->
                            markedFight.scores
                                    ?.any { isc -> isc.competitorId == s.competitorId } != false
                                    && s.parentFightId == markedFight.id
                                    && s.parentReferenceType == FightReferenceType.WINNER
                        } != false
                        , "Fight ${markedFight.id} is uncompletable and has a competitor, but this competitor was not propagated to ${markedFight.winFight}. ")
            }
        }
    }

    private fun createDistributedDoubleElimination(compsSize: Int): List<FightDescriptionDTO> {
        val fights = generateEmptyWinnerFights(compsSize)
        val competitors = FightsService.generateRandomCompetitorsForCategory(compsSize, 20, category, competitionId)
        checkWinnerFightsLaws(fights, 8)
        val doubleEliminationBracketFights = fightsGenerateService.generateLoserBracketAndGrandFinalForWinnerBracket(competitionId, categoryId, stageId, fights, duration, true)
        log.info("${doubleEliminationBracketFights.size}")
        log.info(doubleEliminationBracketFights.joinToString("\n"))
        checkDoubleEliminationLaws(doubleEliminationBracketFights, 8)
        val distr = fightsGenerateService.distributeCompetitors(competitors, doubleEliminationBracketFights, BracketType.DOUBLE_ELIMINATION)
        printFights(distr)
        val marked = FightsService.markAndProcessUncompletableFights(distr, StageStatus.APPROVED) { id -> distr.find { it.id == id }?.scores?.map { it.toPojo(id) } }
        return marked
    }

    @Test
    fun testProcessSingleEliminationBrackets() {
        val fights = generateEmptyWinnerFights(14)
        val competitors = FightsService.generateRandomCompetitorsForCategory(14, 20, category, competitionId)
        val fightsWithCompetitors = fightsGenerateService.distributeCompetitors(competitors, fights, BracketType.SINGLE_ELIMINATION)
        val filledFights = processSingleElimination(fightsWithCompetitors)
        val results = fightsGenerateService.buildStageResults(BracketType.SINGLE_ELIMINATION, StageStatus.FINISHED, StageType.FINAL, filledFights, "asasdasd", competitionId, emptyList())
        assertEquals(14, results.size)
        assertEquals(6, results.filter { it.round == 0 }.size)
        assertEquals(4, results.filter { it.round == 1 }.size)
        assertEquals(2, results.filter { it.round == 2 }.size)
        assertEquals(2, results.filter { it.round == 3 }.size)
    }

    private fun processBracketsRound(roundFights: List<FightDescriptionDTO>): List<Pair<FightDescriptionDTO, String?>> = roundFights.map { generateFightResult(it) }
    private fun fillNextRound(previousRoundResult: List<Pair<FightDescriptionDTO, String?>>, nextRoundFights: List<FightDescriptionDTO>): List<FightDescriptionDTO> {
        return previousRoundResult.fold(nextRoundFights) { acc, pf ->
            val updatedWinFight = pf.second?.let { acc.first { f -> f.id == pf.first.winFight }.pushCompetitor(it, pf.first.id) }
                    ?: acc.first { f -> f.id == pf.first.winFight }
            val loserId = pf.first.scores.find { sc -> sc.competitorId != pf.second }?.competitorId
            val updatedLoseFight = loserId?.let { loser -> acc.find { f -> f.id == pf.first.loseFight }?.pushCompetitor(loser, pf.first.id) }
                    ?: acc.find { f -> f.id == pf.first.loseFight }
            acc.applyConditionalUpdate({ it.id == updatedWinFight.id }, { updatedWinFight }).applyConditionalUpdate({ it.id == updatedLoseFight?.id }, {
                updatedLoseFight ?: it
            })
        }
    }


    @Test
    fun testProcessSingleEliminationBracketsWithThirdPlace() {
        val fights = fightsGenerateService.generateThirdPlaceFightForOlympicSystem(competitionId, categoryId, stageId, generateEmptyWinnerFights(14))
        val competitors = FightsService.generateRandomCompetitorsForCategory(14, 20, category, competitionId)
        val fightsWithCompetitors = fightsGenerateService.distributeCompetitors(competitors, fights, BracketType.SINGLE_ELIMINATION)
        val filledFights = processSingleElimination(fightsWithCompetitors)
        val results = fightsGenerateService.buildStageResults(BracketType.SINGLE_ELIMINATION, StageStatus.FINISHED, StageType.FINAL, filledFights, "asasdasd", competitionId, emptyList())
        assertEquals(14, results.size)
        assertEquals(14, results.distinctBy { it.competitorId }.size)
        assertEquals(6, results.filter { it.round == 0 }.size)
        assertEquals(4, results.filter { it.round == 1 }.size)
        assertEquals(0, results.filter { it.round == 2 }.size)
        assertEquals(4, results.filter { it.round == 3 }.size)
        assertEquals(1, results.filter { it.place == 3 }.size)
        assertEquals(1, results.filter { it.place == 2 }.size)
        assertEquals(1, results.filter { it.place == 1 }.size)
        assertEquals(1, results.filter { it.place == 4 }.size)
        assertEquals(4, results.filter { it.place == 5 }.size)
        assertEquals(6, results.filter { it.place == 7 }.size)
    }

    private val fightJoinToStringTransform = { it: FightDescriptionDTO -> "Id: ${it.id}, status: ${it.status}, round: ${it.round}, roundType: ${it.roundType}, scores: ${it.scores.joinToString { s -> "competitor ${s.competitorId} from fight ${s.parentFightId} type ${s.parentReferenceType}" }}, result: ${it.fightResult}, winfight ${it.winFight}, loseFight: ${it.loseFight}"}
    private val fightHasNoResultPredicate = { it: FightDescriptionDTO -> it.scores?.any { s -> !s.competitorId.isNullOrBlank() } == true && it.fightResult?.winnerId.isNullOrBlank() }
    private val fightDoesNotHaveCompetitorsPredicate = { f: FightDescriptionDTO -> f.status != FightStatus.UNCOMPLETABLE && f.scores?.all { !it.competitorId.isNullOrBlank() } != true }

    @Test
    fun testProcessDoubleEliminationBrackets() {
        val fights = createDistributedDoubleElimination(14)
        val filledFights = processDoubleElimination(fights)

        assertTrue ("Not all fights have competitors. \n" +
                filledFights.filter(fightDoesNotHaveCompetitorsPredicate).joinToString(separator = "\n", transform = fightJoinToStringTransform)) { filledFights.none(fightDoesNotHaveCompetitorsPredicate) }
        assertTrue ("Not all fights have results: \n${filledFights.filter(fightHasNoResultPredicate).joinToString(separator = "\n", transform = fightJoinToStringTransform)}") { filledFights.none(fightHasNoResultPredicate) }
        val results = fightsGenerateService.buildStageResults(BracketType.DOUBLE_ELIMINATION, StageStatus.FINISHED, StageType.FINAL, filledFights, "asasdasd", competitionId, emptyList())
        log.info("\n${results.groupBy { it.place }.toList().sortedBy { it.first }.joinToString ("\n") { "${it.first} -> ${it.second.size}" }}")
        assertEquals(14, results.size)
        assertEquals(14, results.distinctBy { it.competitorId }.size)
        assertEquals(1, results.filter { it.place == 1 }.size)
        assertEquals(1, results.filter { it.place == 2 }.size)
        assertEquals(1, results.filter { it.place == 3 }.size)
        assertEquals(1, results.filter { it.place == 4 }.size)
        assertEquals(2, results.filter { it.place == 5 }.size)
        assertEquals(2, results.filter { it.place == 7 }.size)
        assertEquals(3, results.filter { it.place == 9 }.size)
        assertEquals(3, results.filter { it.place == 12 }.size)
    }

    private fun processDoubleElimination(fightsWithCompetitors: List<FightDescriptionDTO>): List<FightDescriptionDTO> {
        tailrec fun loop(result: List<FightDescriptionDTO>, processedFights: Set<String>): List<FightDescriptionDTO> {
            if (result.isEmpty()) {
                log.warn("Empty input... result: ${result.size}")
                return result
            }
            if (result.all { processedFights.contains(it.id) }) {
                return result.map { generateFightResult(it) }.map { it.first }
            }
            val nextConnectedFights = result.filter { f ->
                !processedFights.contains(f.id)
                        && f.scores?.any { processedFights.contains(it.parentFightId) } == true
            }
            val parentIds = nextConnectedFights.flatMap { it.scores?.mapNotNull { s -> s.parentFightId }.orEmpty() }.toSet()
            val fightsWithResults = result.filter { parentIds.contains(it.id) }.map { generateFightResult(it) }.map { it.first }
            val updatedFights = result.applyConditionalUpdate({ fightsWithResults.exists { fr -> fr.id == it.id } }, { fightsWithResults.first { fr -> fr.id == it.id } })
            val fightsWithWinners = fightsWithResults.fold(updatedFights) { acc, fight ->
                FightsService.getWinnerId(fight)?.let { wid -> FightsService.moveFighterToSiblings(wid, fight.id, FightReferenceType.WINNER, acc) } ?: acc
            }
            val fightsWithLosersAndWinners = fightsWithResults.fold(fightsWithWinners) { acc, fight ->
                FightsService.getLoserId(fight)?.let { wid -> FightsService.moveFighterToSiblings(wid, fight.id, FightReferenceType.LOSER, acc) } ?: acc
            }
            val nextProcFights = processedFights +
                    nextConnectedFights
                            .mapNotNull { it.id }
                            .filter{ f ->
                                val updatedFight = fightsWithLosersAndWinners.first { fw -> fw.id == f }
                                updatedFight.status == FightStatus.UNCOMPLETABLE || updatedFight.scores?.count { !it.competitorId.isNullOrBlank() } == 2
                            }
            return loop(fightsWithLosersAndWinners, nextProcFights)
        }

        val firstRoundFights = fightsWithCompetitors.filter { f -> f.scores?.all { it.parentFightId.isNullOrBlank() } == true }
        val firstRoundIds = firstRoundFights.mapNotNull { it.id }.toSet()

        return loop(fightsWithCompetitors, firstRoundIds)
    }

    private fun processSingleElimination(fightsWithCompetitors: List<FightDescriptionDTO>): List<FightDescriptionDTO> {
        return fightsWithCompetitors.groupBy { it.round!! }.toList().sortedBy { it.first }
                .fold(emptyList<Pair<FightDescriptionDTO, String?>>() to emptyList<FightDescriptionDTO>()) { acc, pair ->
                    val processedFights = if (acc.first.isEmpty() && pair.first == 0) {
                        //this is first round
                        processBracketsRound(pair.second)
                    } else {
                        processBracketsRound(fillNextRound(acc.first, pair.second))
                    }
                    processedFights to (acc.second + processedFights.map { it.first })
                }.second
    }

}