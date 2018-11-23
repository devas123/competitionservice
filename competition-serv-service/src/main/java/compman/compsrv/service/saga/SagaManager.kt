package compman.compsrv.service.saga

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.service.StateQueryService


class SagaManager(private val stateQueryService: StateQueryService) {

    private data class SagaStateWrapper(val state: SagaState, val clazz: Class<out SagaState>, private val type: String)

    private val mapper: ObjectMapper = ObjectMapperFactory.createObjectMapper()

    fun getSaga(sagaData: String?, sagaType: String, competitionId: String, correlationId: String): Saga {
        TODO()
//        val sagaStateWrapper = mapper.readValue(sagaData, SagaStateWrapper::class.java)
//
//        val saga = when (sagaType) {
//            "DROP_BRACKETS_SAGA" -> {
//                val brackets = stateQueryService.getCompetitionProperties(competitionId)?.categories?.mapNotNull {cat ->
//                    cat.id?.let { catId ->
//                        stateQueryService.getCategoryState(catId)?.brackets?.let { cat to it }
//                    }
//                } ?: emptyList()
//                DropBracketsSaga(correlationId, competitionId, brackets)
//            }
//            else -> throw IllegalArgumentException("Unknown saga type: $sagaType")
//        }
//        if (sagaStateWrapper != null) {
//            saga.init(sagaStateWrapper.state)
//        }
//        return saga
    }

    fun serializeSagaState(sagaState: SagaState, sagaType: String): String = mapper.writeValueAsString(SagaStateWrapper(sagaState, sagaState.javaClass, sagaType))
}