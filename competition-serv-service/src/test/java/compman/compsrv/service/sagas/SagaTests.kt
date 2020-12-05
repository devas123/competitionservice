package compman.compsrv.service.sagas

import arrow.core.*
import compman.compsrv.aggregate.Category
import compman.compsrv.aggregate.Competition
import compman.compsrv.aggregate.Competitor
import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.errors.show
import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.dto.competition.RegistrationInfoDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.CategoryGeneratorService
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.service.processor.command.AggregateServiceFactory
import compman.compsrv.service.processor.command.CategoryAggregateService
import compman.compsrv.service.processor.command.CompetitionAggregateService
import compman.compsrv.service.processor.command.CompetitorAggregateService
import compman.compsrv.service.processor.sagas.*
import compman.compsrv.service.schedule.ScheduleService
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
        val commandDTO = CommandDTO().setId("id").setCategoryId("categoryId").setCompetitionId("competitionId").setCompetitorId("competitorId").setType(CommandType.DUMMY_COMMAND)
        val saga = processCommand(commandDTO)
                .andThen({ list ->
                    applyEvents(list.first.right(), list.second)
                }, {_, commandProcessingError -> error(commandProcessingError) })

        saga.log(log)
    }
    @Test
    fun testCompetitorAddSaga() {
        val mapper = ObjectMapperFactory.createObjectMapper()
        val rocksDbOps = mock(DBOperations::class.java)
        val catas = CategoryAggregateService(mock(FightServiceFactory::class.java), mapper, emptyList())
        val comas = CompetitionAggregateService(mock(ScheduleService::class.java), mock(ClusterOperations::class.java), emptyList(), mapper)
        val compas = CompetitorAggregateService(mapper, emptyList())
        val asf = AggregateServiceFactory(catas, comas, compas)
        val commandDTO = CommandDTO().setId("id").setCategoryId("categoryId").setCompetitionId("competitionId").setCompetitorId("competitorId")
            .setType(CommandType.ADD_COMPETITOR_COMMAND)
            .setPayload(CompetitorDTO().setId("competitorId").setCategories(arrayOf("categoryId"))
                .setEmail("email").setFirstName("Vasya").setLastName("Pupoken"))
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
                .andThen({ applyEvent(Unit.left(), EventDTO().setId("id").setVersion(0).setCompetitorId("competitorId").setCompetitionId("competitionId").setCategoryId("categoryId1").setType(EventType.CATEGORY_DELETED))
                    .eventAndThen({ applyEvent(Unit.left(), EventDTO().setId("id").setCompetitorId("competitorId").setCompetitionId("competitionId").setCategoryId("categoryId").setType(EventType.CATEGORY_NUMBER_OF_COMPETITORS_INCREASED)) },
                        { aaa, _ ->
                            applyEvent(aaa, EventDTO().setVersion(0).setCompetitorId("competitorId").setId("id").setType(EventType.COMPETITOR_REMOVED)) } )},
                    { agg, _ ->
                        applyEvent(Either.fromNullable(agg.first), EventDTO().setId("id").setVersion(0).setCompetitorId("competitorId").setType(EventType.COMPETITOR_REMOVED)) } )


        saga.log(log)
        val l = saga.accumulate(rocksDbOps, asf).doRun()
        l.mapLeft { log.error(it.show()) }

        val s = listOf(
             applyEvent(Unit.left(), EventDTO().setId("id").setCompetitorId("competitorId").setVersion(0).setCompetitionId("competitionId").setCategoryId("categoryId1").setType(EventType.CATEGORY_DELETED)),
             applyEvent(Unit.left(), EventDTO().setId("id").setCompetitorId("competitorId").setVersion(0).setCompetitionId("competitionId").setCategoryId("categoryId2").setType(EventType.CATEGORY_DELETED)),
             applyEvent(Unit.left(), EventDTO().setId("id").setCompetitorId("competitorId").setCompetitionId("competitionId").setCategoryId("categoryId3").setType(EventType.CATEGORY_DELETED))
        ).reduce { acc, f -> acc.andStep(f) }



        val m = s.accumulate(rocksDbOps, asf).doRun()

        m.mapLeft { log.error(it.show()) }
    }
}