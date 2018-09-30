package compman.compsrv.service

import compman.compsrv.model.competition.*
import org.junit.Test
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class FightsGenerateServiceTest {
    private val fightsGenerateService = FightsGenerateService()

    companion object {
        const val competitionId = "UG9wZW5nYWdlbiBPcGVu"

        val category = Category(BjjAgeDivisions.ADULT, Gender.MALE, competitionId, UUID.randomUUID().toString(), Weight("Light", BigDecimal.TEN), BeltType.BROWN, BigDecimal(8))
    }

    @Test
    fun testGenerateFights() {
        val competitors = FightsGenerateService.generateRandomCompetitorsForCategory(50, 30, category, competitionId)
        val fights = fightsGenerateService.generatePlayOff(competitors, competitionId)

        fights.forEach {
            assertEquals(competitionId, it.competitionId)
            assertEquals(FightStage.PENDING, it.stage)
            assertNotNull(it.round)
            assertNotNull(it.numberInRound)
            assertNotNull(it.numberOnMat)
        }

    }
}