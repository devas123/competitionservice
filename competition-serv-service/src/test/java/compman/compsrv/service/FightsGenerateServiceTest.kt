package compman.compsrv.service

import compman.compsrv.jpa.competition.AgeDivision
import compman.compsrv.jpa.competition.BeltType
import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.jpa.competition.Weight
import compman.compsrv.model.dto.competition.AgeDivisionDTO
import compman.compsrv.model.dto.competition.FightStage
import compman.compsrv.model.dto.competition.Gender
import compman.compsrv.repository.CategoryDescriptorCrudRepository
import org.junit.Test
import org.springframework.data.domain.Example
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CrudRepoMock(private val category: CategoryDescriptor) : CategoryDescriptorCrudRepository {
    override fun <S : CategoryDescriptor?> save(entity: S): S {
        return category as S
    }

    override fun deleteInBatch(entities: MutableIterable<CategoryDescriptor>) {
    }

    override fun findAll(): MutableList<CategoryDescriptor> {
        return mutableListOf(category)
    }

    override fun findAll(sort: Sort): MutableList<CategoryDescriptor> {
        return mutableListOf(category)
    }

    override fun <S : CategoryDescriptor?> findAll(example: Example<S>): MutableList<S> {
        return mutableListOf(category as S)
    }

    override fun <S : CategoryDescriptor?> findAll(example: Example<S>, sort: Sort): MutableList<S> {
        return mutableListOf(category as S)
    }

    override fun findAll(pageable: Pageable): Page<CategoryDescriptor> {
        return Page.empty()
    }

    override fun <S : CategoryDescriptor?> findAll(example: Example<S>, pageable: Pageable): Page<S> {
        return Page.empty()
    }

    override fun deleteById(id: String) {
    }

    override fun deleteAllInBatch() {
    }

    override fun <S : CategoryDescriptor?> saveAndFlush(entity: S): S {
        return entity
    }

    override fun flush() {
    }

    override fun deleteAll(entities: MutableIterable<CategoryDescriptor>) {
    }

    override fun deleteAll() {

    }

    override fun <S : CategoryDescriptor?> saveAll(entities: MutableIterable<S>): MutableList<S> {
        return entities.toMutableList()
    }

    override fun <S : CategoryDescriptor?> findOne(example: Example<S>): Optional<S> {
        return Optional.of(category as S)
    }

    override fun count(): Long {
        return 1
    }

    override fun <S : CategoryDescriptor?> count(example: Example<S>): Long {
        return 1
    }

    override fun getOne(id: String): CategoryDescriptor {
        return category
    }

    override fun findAllById(ids: MutableIterable<String>): MutableList<CategoryDescriptor> {
        return listOf(category).toMutableList()
    }

    override fun existsById(id: String): Boolean {
        return true
    }

    override fun <S : CategoryDescriptor?> exists(example: Example<S>): Boolean {
        return true
    }

    override fun findById(id: String): Optional<CategoryDescriptor> = Optional.of(category)
    override fun delete(entity: CategoryDescriptor) {
    }

}

class FightsGenerateServiceTest {
    private val fightsGenerateService = FightsGenerateService(CrudRepoMock(category))

    companion object {
        const val competitionId = "UG9wZW5nYWdlbiBPcGVu"

        val category = CategoryDescriptor("BJJ", AgeDivision.fromDTO(AgeDivisionDTO.ADULT), Gender.MALE.name, Weight("Light", BigDecimal.TEN), BeltType.BROWN, UUID.randomUUID().toString(), BigDecimal(8))
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