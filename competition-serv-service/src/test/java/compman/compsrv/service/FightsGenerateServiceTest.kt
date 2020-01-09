package compman.compsrv.service

import compman.compsrv.mapping.toEntity
import compman.compsrv.model.dto.competition.FightStage
import compman.compsrv.repository.CategoryDescriptorCrudRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@RunWith(MockitoJUnitRunner::class)
class FightsGenerateServiceTest {
    private val crudRepo = mock(CategoryDescriptorCrudRepository::class.java)!!
    private val fightsGenerateService = FightsGenerateService(crudRepo)

    companion object {
        const val competitionId = "UG9wZW5nYWdlbiBPcGVu"

//        val category = CategoryDescriptor(competitionId, "BJJ", AgeDivisionDTO.ADULT.toEntity(), mutableSetOf(), Gender.MALE.name, Weight("Light", BigDecimal.TEN), BeltType.BROWN, UUID.randomUUID().toString(), BigDecimal(8))
        val category = CategoryGeneratorService.createCategory(8, CategoryGeneratorService.bjj, CategoryGeneratorService.adult, CategoryGeneratorService.male, CategoryGeneratorService.admlight, CategoryGeneratorService.brown).toEntity(competitionId) {null}
    }


    @Test
    fun testGenerateFights() {

        `when`(crudRepo.findById(ArgumentMatchers.anyString())).thenReturn(Optional.of(category))

        val competitors = FightsGenerateService.generateRandomCompetitorsForCategory(50, 30, category, competitionId)
        val fights = fightsGenerateService.generateRoundsForCategory(category.id!!, competitors, competitionId)

        fights.forEach {
            assertEquals(competitionId, it.competitionId)
            assertEquals(FightStage.PENDING, it.stage)
            assertNotNull(it.round)
            assertNotNull(it.numberInRound)
            assertNotNull(it.numberOnMat)
        }

    }
}