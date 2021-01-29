package compman.compsrv.service.processor

import compman.compsrv.errors.show
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.service.processor.saga.log
import compman.compsrv.util.IDGenerator
import org.junit.Before
import kotlin.test.*

class SagaApplicationTests : AbstractCompetitionLogicTestBase() {

    @Before
    fun before() {
        initCompetitionState()
    }

    @Test
    fun testChangeCategory() {
        val dbOperations = repo.getOperations()
        val changeCategory = CommandDTO().apply {
            id = IDGenerator.uid()
            this.categoryId = this@SagaApplicationTests.categoryId
            this.competitionId = this@SagaApplicationTests.competitionId
            this.competitorId = this@SagaApplicationTests.competitorId
            type = CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND
            payload = ChangeCompetitorCategoryPayload().apply {
                fighterId = this@SagaApplicationTests.competitorId
                oldCategoryId = this@SagaApplicationTests.categoryId
                newCategoryId = this@SagaApplicationTests.newCategoryId
            }
        }

        val saga = changeCompetitorCategory.createSaga(dbOperations, changeCategory)
        saga.map { it.log(log) }

        val events = changeCompetitorCategory.runSaga(dbOperations, saga)
        log.info(events.mapLeft { it.show() }.toString())

        val competitor = dbOperations.getCompetitor(competitorId)
        val category = dbOperations.getCategory(categoryId)
        val newCategory = dbOperations.getCategory(newCategoryId)
        assertNotNull(competitor.competitorDTO.categories)
        assertFalse { competitor.competitorDTO.categories.contains(categoryId) }
        assertTrue { competitor.competitorDTO.categories.contains(newCategoryId) }
        assertTrue { newCategory.numberOfCompetitors == 1 }
        assertTrue { category.numberOfCompetitors == 0 }
        assertTrue { events.isRight() }
        val unwrappedEvts = events.fold({ emptyList<EventDTO>() }, { it })
        val increasedEvent = unwrappedEvts.find { it.type == EventType.CATEGORY_NUMBER_OF_COMPETITORS_INCREASED }
        val decreasedEvent = unwrappedEvts.find { it.type == EventType.CATEGORY_NUMBER_OF_COMPETITORS_DECREASED }
        val competitorUpdated = unwrappedEvts.find { it.type == EventType.COMPETITOR_UPDATED }
        assertNotNull(increasedEvent)
        assertNotNull(decreasedEvent)
        assertNotNull(competitorUpdated)
        assertEquals(categoryId, decreasedEvent.categoryId)
        assertEquals(newCategoryId, increasedEvent.categoryId)
        assertEquals(competitorId, competitorUpdated.competitorId)
    }

    @Test
    fun testChangeCategoryWrongCategoryId() {
        val dbOperations = repo.getOperations()
        val wrongCategoryId = "wrongCategoryId"
        val changeCategory = CommandDTO().apply {
            id = IDGenerator.uid()
            this.categoryId = this@SagaApplicationTests.categoryId
            this.competitionId = this@SagaApplicationTests.competitionId
            this.competitorId = this@SagaApplicationTests.competitorId
            type = CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND
            payload = ChangeCompetitorCategoryPayload().apply {
                fighterId = this@SagaApplicationTests.competitorId
                oldCategoryId = this@SagaApplicationTests.categoryId
                newCategoryId = wrongCategoryId
            }
        }

        val saga = changeCompetitorCategory.createSaga(dbOperations, changeCategory)
        saga.map { it.log(log) }

        val events = changeCompetitorCategory.runSaga(dbOperations, saga)
        log.info(events.mapLeft { it.show() }.toString())

        val competitor = dbOperations.getCompetitor(competitorId)
        val category = dbOperations.getCategory(categoryId)
        val newCategory = dbOperations.getCategory(newCategoryId)
        assertNotNull(competitor.competitorDTO.categories)
        assertTrue { competitor.competitorDTO.categories.contains(categoryId) }
        assertFalse { competitor.competitorDTO.categories.contains(wrongCategoryId) }
        assertTrue { newCategory.numberOfCompetitors == 0 }
        assertTrue { category.numberOfCompetitors == 1 }
        assertTrue { events.isLeft() }
//        val unwrappedEvts = events.fold({ emptyList<EventDTO>() }, { it })
//        val increasedEvent = unwrappedEvts.find { it.type == EventType.CATEGORY_NUMBER_OF_COMPETITORS_INCREASED }
//        val decreasedEvent = unwrappedEvts.find { it.type == EventType.CATEGORY_NUMBER_OF_COMPETITORS_DECREASED }
//        val competitorUpdated = unwrappedEvts.find { it.type == EventType.COMPETITOR_UPDATED }
//        assertNotNull(increasedEvent)
//        assertNotNull(decreasedEvent)
//        assertNotNull(competitorUpdated)
//        assertEquals(categoryId, decreasedEvent.categoryId)
//        assertEquals(newCategoryId, increasedEvent.categoryId)
//        assertEquals(competitorId, competitorUpdated.competitorId)
    }
}