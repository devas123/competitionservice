package compman.compsrv.service

import compman.compsrv.mapping.toPojo
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.service.fight.BracketsGenerateService
import compman.compsrv.service.fight.FightsService
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
        val fights = generateEmptyWinnerFights(14)
        val competitors = FightsService.generateRandomCompetitorsForCategory(14, 20, category, competitionId)
        checkWinnerFightsLaws(fights, 8)
        val doubleEliminationBracketFights = fightsGenerateService.generateLoserBracketAndGrandFinalForWinnerBracket(competitionId, categoryId, stageId, fights, duration, true)
        log.info("${doubleEliminationBracketFights.size}")
        log.info(doubleEliminationBracketFights.joinToString("\n"))
        checkDoubleEliminationLaws(doubleEliminationBracketFights, 8)
        val distr = fightsGenerateService.distributeCompetitors(competitors, doubleEliminationBracketFights, BracketType.DOUBLE_ELIMINATION)
        printFights(distr)
        val marked = FightsService.markAndProcessUncompletableFights(distr, StageStatus.APPROVED) { id -> distr.find { it.id == id }?.scores?.map { it.toPojo(id) } }
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

    @Test
    fun testProcessBrackets() {
        val fights = generateEmptyWinnerFights(14)
        val competitors = FightsService.generateRandomCompetitorsForCategory(14, 20, category, competitionId)
        val fightsWithCompetitors = fightsGenerateService.distributeCompetitors(competitors, fights, BracketType.SINGLE_ELIMINATION)
        fun processBracketsRound(roundFights: List<FightDescriptionDTO>): List<Pair<FightDescriptionDTO, String?>> = roundFights.map { generateFightResult(it) }
        fun fillNextRound(previousRoundResult: List<Pair<FightDescriptionDTO, String?>>, nextRoundFights: List<FightDescriptionDTO>): List<FightDescriptionDTO> {
            return previousRoundResult.fold(nextRoundFights) { acc, pf ->
                val updatedFight = pf.second?.let { acc.first { f -> f.id == pf.first.winFight }.pushCompetitor(it, pf.first.id) }
                        ?: acc.first { f -> f.id == pf.first.winFight }
                acc.map { if (it.id == updatedFight.id) updatedFight else it }
            }
        }

        val filledFights = fightsWithCompetitors.groupBy { it.round!! }.toList().sortedBy { it.first }.fold(emptyList<Pair<FightDescriptionDTO, String?>>() to emptyList<FightDescriptionDTO>()) { acc, pair ->
            val processedFights = if (acc.first.isEmpty() && pair.first == 0) {
                //this is first round
                processBracketsRound(pair.second)
            } else {
                processBracketsRound(fillNextRound(acc.first, pair.second))
            }
            processedFights to (acc.second + processedFights.map { it.first })
        }.second
        val results = fightsGenerateService.buildStageResults(BracketType.SINGLE_ELIMINATION, StageStatus.FINISHED, StageType.FINAL, filledFights, "asasdasd", competitionId, emptyList())
        assertEquals(14, results.size)
        assertEquals(6, results.filter { it.round == 0 }.size)
        assertEquals(4, results.filter { it.round == 1 }.size)
        assertEquals(2, results.filter { it.round == 2 }.size)
        assertEquals(2, results.filter { it.round == 3 }.size)
    }

}