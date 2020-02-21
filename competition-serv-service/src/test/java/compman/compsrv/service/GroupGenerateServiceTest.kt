package compman.compsrv.service

import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.brackets.GroupDescriptorDTO
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.fight.GroupStageGenerateService
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


@RunWith(MockitoJUnitRunner::class)
class GroupGenerateServiceTest : AbstractGenerateServiceTest() {
    private val fightsGenerateService = GroupStageGenerateService()

    private fun generateGroupFights(competitorsSize: Int): Pair<StageDescriptorDTO, List<FightDescriptionDTO>> {
        val groupId = "$stageId-group0"
        val stage = StageDescriptorDTO()
                .setId(stageId)
                .setStageOrder(0)
                .setBracketType(BracketType.GROUP)
                .setGroupDescriptors(arrayOf(
                        GroupDescriptorDTO()
                                .setSize(competitorsSize)
                                .setId(groupId)
                                .setName("Valera_group")
                ))
        val competitors = FightsService.generateRandomCompetitorsForCategory(competitorsSize, 20, category, competitionId)
        val fights = fightsGenerateService.generateStageFights(competitionId, categoryId, stage, competitorsSize, duration, competitors, 0)
        assertNotNull(fights)
        assertEquals(competitorsSize * (competitorsSize - 1) / 2, fights.size)
        assertTrue(fights.all { it.scores.size == 2 }) //all fights are packed
        assertTrue(fights.all { it.groupId == groupId }) //all fights have a group id
        assertTrue(competitors.all { comp -> fights.filter { f -> f.scores.any { it.competitor.id == comp.id } }.size == competitorsSize - 1 }) //each fighter fights with all the other fighters
        return stage to fights
    }

    @Test
    fun testGenerateGroupFights() {
        generateGroupFights(14)
    }

    @Test
    fun testGenerateResults() {
        val compsSize = 5
        val sf = generateGroupFights(compsSize)
        val fightsWithResult = sf.second.map { generateFightResult(it).first }
        val results = fightsGenerateService.buildStageResults(sf.first.bracketType, StageStatus.FINISHED, fightsWithResult, sf.first.id, competitionId,
                fightResultOptions)
        assertNotNull(results)
        assertEquals(compsSize, results.size, "${results.map { "${it.competitorId}-${it.place}" }}")
        assertTrue(results.all { it.place != null && it.place >= 0 })
        assertTrue(results.all { it.points != null && it.points >= BigDecimal.ZERO })
        assertFalse(results.all { it.points == BigDecimal.ZERO })
        assertEquals(results.size, results.distinctBy { it.place }.size)
    }

}