package compman.compsrv.service.sagas

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import compman.compsrv.errors.show
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.AddCompetitorPayload
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AggregateServiceFactory
import compman.compsrv.service.processor.category.CategoryAggregateService
import compman.compsrv.service.processor.competition.CompetitionAggregateService
import compman.compsrv.service.processor.competitor.CompetitorAggregateService
import compman.compsrv.service.processor.saga.*
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
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
        val commandDTO = CommandDTO().apply {
            id = "id"
            categoryId = "categoryId"
            competitionId = "competitionId"
            competitorId = "competitorId"
            type = CommandType.DUMMY_COMMAND
        }
        val saga = processCommand(commandDTO)
            .andThen({ list ->
                applyEvents(list.first.right(), list.second)
            }, { _, commandProcessingError -> error(commandProcessingError) })

        saga.log(log)
    }

    @Test
    fun testCompetitorAddSaga() {
//        val mapper = ObjectMapperFactory.createObjectMapper()
        val rocksDbOps = mock(DBOperations::class.java)
        val catas = CategoryAggregateService(emptyList(), emptyList())
        val comas = CompetitionAggregateService(
                emptyList(),
                emptyList()
        )
        val compas = CompetitorAggregateService(emptyList(), emptyList())
        val asf = AggregateServiceFactory(catas, comas, compas)
        val commandDTO = CommandDTO().apply {
            id = "id"
            categoryId = "categoryId"
            competitionId = "competitionId"
            competitorId = "competitorId"
            type = CommandType.ADD_COMPETITOR_COMMAND
            payload = AddCompetitorPayload().setCompetitor(
                CompetitorDTO().setId("competitorId").setCategories(arrayOf("categoryId"))
                    .setEmail("email").setFirstName("Vasya").setLastName("Pupoken")
            )
        }
//        `when`(rocksDbOps.getCategory("categoryId1", true)).thenReturn(Category("categoryId1", CategoryDescriptorDTO()))
//        `when`(rocksDbOps.getCategory("categoryId2", true)).thenReturn(Category("categoryId2", CategoryDescriptorDTO()))
//        `when`(rocksDbOps.getCategory("categoryId3", true)).thenReturn(Category("categoryId3", CategoryDescriptorDTO()))
//        `when`(rocksDbOps.getCompetition("competitionId", true)).thenReturn(Competition(
//            id = "competitionId",
//            properties = CompetitionPropertiesDTO(),
//            registrationInfo = RegistrationInfoDTO()
//        ))
//        `when`(rocksDbOps.getCompetitor("competitorId", true)).thenReturn(Competitor(CompetitorDTO().setId("competitorId")))
        val saga = processCommand(commandDTO)
            .andThen({
                applyEvent(Unit.left(), EventDTO().apply {
                    id = "id"
                    version = 0
                    competitorId = "competitorId"
                    competitionId = "competitionId"
                    categoryId = "categoryId1"
                    type = EventType.CATEGORY_DELETED
                })
                    .eventAndThen({
                        applyEvent(Unit.left(), EventDTO().apply {
                            id = "id"
                            competitorId = "competitorId"
                            competitionId = "competitionId"
                            categoryId = "categoryId"
                            type = EventType.CATEGORY_NUMBER_OF_COMPETITORS_INCREASED
                        })
                    },
                        { aaa, _ ->
                            applyEvent(aaa,
                                EventDTO().apply {
                                    version = 0
                                    competitorId = "competitorId"
                                    id = "id"
                                    type = EventType.COMPETITOR_REMOVED
                                }
                            )
                        })
            },
                { agg, _ ->
                    applyEvent(Either.fromNullable(agg.first), EventDTO().apply {
                        id = "id"
                        version = 0
                        competitorId = "competitorId"
                        type = EventType.COMPETITOR_REMOVED
                    })
                })


        saga.log(log)
        val l = saga.accumulate(rocksDbOps, asf).doRun()
        l.mapLeft { log.error(it.show()) }

        val s = listOf(
            applyEvent(
                Unit.left(),
                EventDTO().apply {
                    id = "id"
                    competitorId = "competitorId"
                    version = 0
                    competitionId = "competitionId"
                    categoryId = "categoryId1"
                    type = EventType.CATEGORY_DELETED
                }
            ),
            applyEvent(
                Unit.left(),
                EventDTO().apply {
                    id = "id"
                    competitorId = "competitorId"
                    version = 0
                    competitionId = "competitionId"
                    categoryId = "categoryId2"
                    type = EventType.CATEGORY_DELETED
                }
            ),
            applyEvent(
                Unit.left(),
                EventDTO().apply {
                    id = "id"
                    competitorId = "competitorId"
                    competitionId = "competitionId"
                    categoryId = "categoryId3"
                    type = EventType.CATEGORY_DELETED
                }
            )
        ).reduce { acc, f -> acc.andStep(f) }

        val m = s.accumulate(rocksDbOps, asf).doRun()
        m.mapLeft { log.error(it.show()) }
    }
}