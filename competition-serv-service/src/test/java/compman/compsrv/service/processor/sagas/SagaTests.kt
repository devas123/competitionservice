package compman.compsrv.service.processor.sagas

import compman.compsrv.aggregate.Competitor
import compman.compsrv.errors.show
import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.DelegatingAggregateService
import compman.compsrv.service.processor.saga.ChangeCompetitorCategory
import compman.compsrv.service.processor.saga.log
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.slf4j.LoggerFactory
import kotlin.test.Test

@RunWith(MockitoJUnitRunner::class)
class SagaTests {

    companion object {
        private val log = LoggerFactory.getLogger(SagaTests::class.java)
    }

    @Test
    fun testSagaLogging() {
        val aggregateServiceFactory = mock(DelegatingAggregateService::class.java)
        val dbOperations = mock(DBOperations::class.java)
        `when`(dbOperations.getCompetitor(anyString(), anyBoolean())).thenReturn(
            Competitor(CompetitorDTO()
                .apply
                { categories = arrayOf("categoryId") })
        )

        val changeCompetitorCategory =
            ChangeCompetitorCategory(ObjectMapperFactory.createObjectMapper(), emptyList(), aggregateServiceFactory)

        val command = CommandDTO().apply {
            id = "id"
            categoryId = "categoryId"
            competitionId = "competitionId"
            competitorId = "competitorId"
            type = CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND
            payload = ChangeCompetitorCategoryPayload().apply {
                fighterId = "fighterId"
                oldCategoryId = "categoryId"
                newCategoryId = "newCategoryId"
            }
        }


        val saga = changeCompetitorCategory.createSaga(dbOperations, command)
        saga.map { it.log(log) }

        val events = changeCompetitorCategory.runSaga(dbOperations, saga)
        log.info(events.mapLeft { it.show() }.toString())
    }
}