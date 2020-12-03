package compman.compsrv.service.sagas

import arrow.core.*
import arrow.core.extensions.either.monad.monad
import compman.compsrv.errors.show
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.RocksDBOperations
import compman.compsrv.service.processor.command.AggregateServiceFactory
import compman.compsrv.service.processor.command.CategoryAggregateService
import compman.compsrv.service.processor.command.CompetitionAggregateService
import compman.compsrv.service.processor.command.CompetitorAggregateService
import compman.compsrv.service.processor.sagas.*
import compman.compsrv.service.processor.sagas.fix
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
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
                    if (list.isEmpty()) {
                        applyEvents(Unit.left(), emptyList())
                    } else {
                        list.map { applyEvents(it.first.right(), it.second) }
                            .reduce { acc, free -> and(acc, free) }
                    }
                }, {_, commandProcessingError -> error(commandProcessingError) })

        saga.log(log)
    }
    @Test
    fun testCompetitorAddSaga() {
        val rocksDbOps = Mockito.mock(RocksDBOperations::class.java)
        val catas = Mockito.mock(CategoryAggregateService::class.java)
        val comas = Mockito.mock(CompetitionAggregateService::class.java)
        val compas = Mockito.mock(CompetitorAggregateService::class.java)
        val asf = AggregateServiceFactory(catas, comas, compas)

        val commandDTO = CommandDTO().setId("id").setCategoryId("categoryId").setCompetitionId("competitionId").setCompetitorId("competitorId").setType(CommandType.ADD_COMPETITOR_COMMAND)
        val saga = processCommand(commandDTO)
                .andThen({ applyEvent(Unit.left(), EventDTO().setId("id").setType(EventType.CATEGORY_NUMBER_OF_COMPETITORS_INCREASED)) },
                    { agg, _ ->
                        applyEvent(Either.fromNullable(agg.firstOrNull()?.first), EventDTO().setId("id").setType(EventType.COMPETITOR_REMOVED)) } )

        saga.log(log)
        val l = saga.accumulate(rocksDbOps, asf).doRun()
        l.mapLeft { log.error(it.show()) }
    }
}