package compman.compsrv.service

import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.brackets.CompetitorResultType
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightResultDTO
import compman.compsrv.service.fight.BracketsGenerateService
import compman.compsrv.service.fight.FightsService
import compman.compsrv.util.copy
import compman.compsrv.util.pushCompetitor
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import kotlin.random.Random
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
        assertTrue { fights.filter { it.round == 0 }.none { it.parentId1 != null || it.parentId2 != null } }
        assertTrue { fights.filter { it.round != 0 }.none { it.parentId1 == null || it.parentId2 == null } }
        assertTrue { fights.filter { it.round == 5 }.none { it.winFight != null } }
        assertTrue { fights.filter { it.round != 5 }.none { it.winFight == null } }
    }

    @Test
    fun testGenerateEmptyDoubleEliminationFights() {
        val fights = generateEmptyWinnerFights(14)
        assertEquals(8 + 4 + 2 + 1, fights.size)
        assertEquals(8, fights.filter { it.round == 0 }.size)
        assertEquals(4, fights.filter { it.round == 1 }.size)
        assertEquals(2, fights.filter { it.round == 2 }.size)
        assertEquals(1, fights.filter { it.round == 3 }.size)
        assertTrue { fights.filter { it.round == 0 }.none { it.parentId1 != null || it.parentId2 != null } }
        assertTrue { fights.filter { it.round != 0 }.none { it.parentId1 == null || it.parentId2 == null } }
        assertTrue { fights.filter { it.round == 3 }.none { it.winFight != null } }
        assertTrue { fights.filter { it.round != 3 }.none { it.winFight == null } }
        val doubleEliminationBracketFights = fightsGenerateService.generateLoserBracketAndGrandFinalForWinnerBracket(competitionId, categoryId, stageId, fights, duration, true)
        log.info("${doubleEliminationBracketFights.size}")
        log.info(doubleEliminationBracketFights.joinToString("\n"))
        assertEquals(30, doubleEliminationBracketFights.size)
        val loserBracketsFights = doubleEliminationBracketFights.filter { it.roundType == StageRoundType.LOSER_BRACKETS }
        assertEquals(14, loserBracketsFights.size)
        assertEquals(4, loserBracketsFights.filter { it.round == 0 }.size)
        assertEquals(4, loserBracketsFights.filter { it.round == 1 }.size)
        assertEquals(2, loserBracketsFights.filter { it.round == 2 }.size)
        assertEquals(2, loserBracketsFights.filter { it.round == 3 }.size)
        assertEquals(1, loserBracketsFights.filter { it.round == 4 }.size)
        assertEquals(1, loserBracketsFights.filter { it.round == 5 }.size)
        assertEquals(1, doubleEliminationBracketFights.filter { it.roundType == StageRoundType.GRAND_FINAL }.size)

    }

    @Test
    fun testGenerateThirdPlaceFight() {
        val fights = generateEmptyWinnerFights(14)
        assertEquals(8 + 4 + 2 + 1, fights.size)
        assertEquals(8, fights.filter { it.round == 0 }.size)
        assertEquals(4, fights.filter { it.round == 1 }.size)
        assertEquals(2, fights.filter { it.round == 2 }.size)
        assertEquals(1, fights.filter { it.round == 3 }.size)
        assertTrue { fights.filter { it.round == 0 }.none { it.parentId1 != null || it.parentId2 != null } }
        assertTrue { fights.filter { it.round != 0 }.none { it.parentId1 == null || it.parentId2 == null } }
        assertTrue { fights.filter { it.round == 3 }.none { it.winFight != null } }
        assertTrue { fights.filter { it.round != 3 }.none { it.winFight == null } }
        val withThirdPlaceFight = fightsGenerateService.generateThirdPlaceFightForOlympicSystem(competitionId, categoryId, stageId, fights)
        log.info("${withThirdPlaceFight.size}")
        log.info(withThirdPlaceFight.joinToString("\n"))
        assertEquals(fights.size + 1, withThirdPlaceFight.size)
        assertTrue { withThirdPlaceFight.count { it.roundType == StageRoundType.THIRD_PLACE_FIGHT } == 1 }
    }

    @Test
    fun testDistributeCompetitors() {
        val fights = generateEmptyWinnerFights(14)
        val competitors = FightsService.generateRandomCompetitorsForCategory(14, 20, category, competitionId)
        val fightsWithCompetitors = fightsGenerateService.distributeCompetitors(competitors, fights, BracketType.SINGLE_ELIMINATION)
    }

    @Test
    fun testProcessBrackets() {
        val fights = generateEmptyWinnerFights(14)
        val competitors = FightsService.generateRandomCompetitorsForCategory(14, 20, category, competitionId)
        val fightsWithCompetitors = fightsGenerateService.distributeCompetitors(competitors, fights, BracketType.SINGLE_ELIMINATION)
        fun generateFightResult(fight: FightDescriptionDTO): Pair<FightDescriptionDTO, CompetitorDTO?> {
            val scores = fight.scores?.toList()
             val competitor = when (scores?.size) {
                2 -> {
                    scores[Random.nextInt(2)].competitor
                }
                1 -> {
                    scores.first().competitor
                }
                else -> {
                   null
                }
            }
            return fight.copy(fightResult = competitor?.let { FightResultDTO(it.id, CompetitorResultType.values()[Random.nextInt(3)], "bla bla bla") }) to competitor
        }
        fun processBracketsRound(roundFights: List<FightDescriptionDTO>): List<Pair<FightDescriptionDTO, CompetitorDTO?>> = roundFights.map { generateFightResult(it) }
        fun fillNextRound(previousRoundResult: List<Pair<FightDescriptionDTO, CompetitorDTO?>>, nextRoundFights: List<FightDescriptionDTO>): List<FightDescriptionDTO> {
            return previousRoundResult.fold(nextRoundFights) { acc, pf ->
                val updatedFight = pf.second?.let { acc.first { f -> f.id == pf.first.winFight }.pushCompetitor(it) }
                        ?: acc.first { f -> f.id == pf.first.winFight }
                acc.map { if (it.id == updatedFight.id) updatedFight else it }
            }
        }

        val filledFights = fightsWithCompetitors.groupBy { it.round!! }.toList().sortedBy { it.first }.fold(emptyList<Pair<FightDescriptionDTO, CompetitorDTO?>>() to emptyList<FightDescriptionDTO>()) { acc, pair ->
            val processedFights = if (acc.first.isEmpty() && pair.first == 0) {
                //this is first round
                processBracketsRound(pair.second)
            } else {
                processBracketsRound(fillNextRound(acc.first, pair.second))
            }
            processedFights to (acc.second + processedFights.map {it.first})
        }.second
        val results = fightsGenerateService.buildStageResults(BracketType.SINGLE_ELIMINATION, StageStatus.FINISHED, filledFights, "asasdasd", competitionId, emptyList())
        assertEquals(14, results.size)
        assertEquals(6, results.filter { it.round == 0 }.size)
        assertEquals(4, results.filter { it.round == 1 }.size)
        assertEquals(2, results.filter { it.round == 2 }.size)
        assertEquals(2, results.filter { it.round == 3 }.size)
    }
}