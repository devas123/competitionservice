package compman.compsrv.service.sagas

import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.service.processor.sagas.*
import org.slf4j.LoggerFactory
import kotlin.test.Test

class SagaTests {

    companion object {
        private val log = LoggerFactory.getLogger(SagaTests::class.java)
    }

    @Test
    fun testSagaLogging() {
        val commandDTO = CommandDTO().setId("id").setCategoryId("categoryId").setCompetitionId("competitionId").setCompetitorId("competitorId").setType(CommandType.DUMMY_COMMAND)
        val saga = processCommand(commandDTO)
                .step({ list: List<EventDTO> ->
                    applyEvents(list)
                }, {commandProcessingError -> error(commandProcessingError) })

        saga.log(log)

    }
}