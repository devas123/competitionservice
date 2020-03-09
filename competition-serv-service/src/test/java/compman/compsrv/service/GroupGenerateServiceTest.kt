package compman.compsrv.service

import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.fight.GroupStageGenerateService
import compman.compsrv.service.fight.dsl.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import kotlin.test.*


@RunWith(MockitoJUnitRunner::class)
class GroupGenerateServiceTest : AbstractGenerateServiceTest() {
    private val fightsGenerateService = GroupStageGenerateService()

    private val log: Logger = LoggerFactory.getLogger(GroupGenerateServiceTest::class.java)

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

    private fun generateEmptyGroupFights(competitorsSize: Int, numberOfGroups: Int): Pair<StageDescriptorDTO, List<FightDescriptionDTO>> {
        val groups = (0 until numberOfGroups).map {
            val groupId = "$stageId-group$it"
            GroupDescriptorDTO()
                    .setSize(competitorsSize)
                    .setId(groupId)
                    .setName("Valera_group-${it + 1}")
        }.toTypedArray()
        val stage = StageDescriptorDTO()
                .setId(stageId)
                .setStageOrder(1)
                .setBracketType(BracketType.GROUP)
                .setInputDescriptor(StageInputDescriptorDTO().setId(stageId).setNumberOfCompetitors(competitorsSize * numberOfGroups))
                .setGroupDescriptors(groups)
        val competitors = emptyList<CompetitorDTO>()
        val fights = fightsGenerateService.generateStageFights(competitionId, categoryId, stage, competitorsSize * numberOfGroups, duration, competitors, 0)
        assertNotNull(fights)
        assertTrue(fights.none { it.groupId.isNullOrBlank() })
        assertEquals(fights.size, fights.distinctBy { it.id }.size)
        val groupedFights = fights.groupBy { it.groupId }
        assertEquals(numberOfGroups, groupedFights.keys.size)
        groupedFights.forEach { (_, fs) ->
            assertTrue(fs.all { it.scores.size == 2 }) //all fights are packed
            assertEquals(competitorsSize * (competitorsSize - 1) / 2, fs.size)
        }
        return stage to fights
    }

    @Test
    fun testGenerateGroupFights() {
        generateGroupFights(14)
    }

    @Test
    fun testGenerateEmptyMultiGroupFights() {
        generateEmptyGroupFights(8, 3)
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

        val program = firstNPlaces(2) + lastNPlaces(2) + manual(listOf("a", "b", "c")) + passedToRound(0, StageRoundType.GROUP)

        program.log(log)
        val selected = program.failFast(results.toTypedArray(), fightsWithResult, fightResultOptions)
        selected.fold( { fail(it.toString()) }, {println(it.joinToString("\n"))})
    }
}