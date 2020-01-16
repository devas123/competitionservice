package compman.compsrv.service

import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.repository.CategoryDescriptorCrudRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@RunWith(MockitoJUnitRunner::class)
class FightsGenerateServiceTest {
    private val crudRepo = mock(CategoryDescriptorCrudRepository::class.java)!!
    private val fightsGenerateService = FightsGenerateService(crudRepo)

    companion object {
        const val competitionId = "UG9wZW5nYWdlbiBPcGVu"
        const val categoryId = "UG9wZW5nYWdlbiBPcGVu-UG9wZW5nYWdlbiBPcGVu"
        val category = CategoryGeneratorService.createCategory(8, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown).toEntity(competitionId) { null }
        private val log = LoggerFactory.getLogger(FightsGenerateServiceTest::class.java)
    }

    private fun generateEmptyWinnerFights(compsSize: Int): List<FightDescription> {
        Mockito.`when`(crudRepo.findById(ArgumentMatchers.anyString())).thenReturn(Optional.of(category))
        val fights = fightsGenerateService.generateEmptyWinnerRoundsForCategory(competitionId, categoryId, compsSize)
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
        val doubleEliminationBracketFights = fightsGenerateService.generateLoserBracketAndGrandFinalForWinnerBracket(competitionId, categoryId, fights, true)
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
        val withThirdPlaceFight = fightsGenerateService.generateThirdPlaceFightForOlympicSystem(competitionId, categoryId, fights)
        log.info("${withThirdPlaceFight.size}")
        log.info(withThirdPlaceFight.joinToString("\n"))
        assertEquals(fights.size + 1, withThirdPlaceFight.size)
    }

    @Test
    fun testDistributeCompetitors() {
        val fights = generateEmptyWinnerFights(14)
        val competitors = FightsGenerateService.generateRandomCompetitorsForCategory(14, 20, category, competitionId)
        val fightsWithCompetitors = fightsGenerateService.distributeCompetitors(competitors, fights, BracketType.SINGLE_ELIMINATION)
        log.info(fightsWithCompetitors.joinToString("\n"))
    }
}