package compman.compsrv.service

import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.model.competition.*
import org.junit.Test
import java.math.BigDecimal
import java.util.*


class FightsGenerateServiceTest {
    private val fightsGenerateService = FightsGenerateService()

    companion object {
        const val competitionId = "UG9wZW5nYWdlbiBPcGVu"

        val category = Category(BjjAgeDivisions.ADULT, Gender.MALE, competitionId, UUID.randomUUID().toString(), Weight("Light", BigDecimal.TEN), BeltType.BROWN, BigDecimal(8))
    }

    @Test
    fun testGenerateFights() {
        val mapper = ObjectMapperFactory.createObjectMapper()
        val competitors = FightsGenerateService.generateRandomCompetitorsForCategory(50, 30, category, competitionId)
        val fights = fightsGenerateService.generatePlayOff(competitors, competitionId)

        println(mapper.writeValueAsString(fights))

    }
}