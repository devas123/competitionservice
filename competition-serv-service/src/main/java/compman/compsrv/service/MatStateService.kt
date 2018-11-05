package compman.compsrv.service

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.competition.FightDescription
import compman.compsrv.model.competition.MatState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import org.springframework.stereotype.Component

@Component
class MatStateService(private val mapper: ObjectMapper) : ICommandProcessingService<MatState, Command, EventHolder> {


    private fun <T> getPayloadAs(payload: Any?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.convertValue(payload, clazz)
        }
        return null
    }

    override fun apply(event: EventHolder, state: MatState?): Pair<MatState?, List<EventHolder>> {
        return when (event.type) {
            EventType.MAT_STATE_INITIALIZED -> {
                val initState = getPayloadAs(event.payload?.get("matState"), MatState::class.java)
                return initState to listOf(event)
            }
            else -> state to emptyList()
        }
    }

    override fun process(command: Command, state: MatState?): List<EventHolder> {
        fun createEvent(type: EventType, payload: Map<String, Any?>) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId
                ?: "null", command.matId, type, payload)

        fun createErrorEvent(error: String) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId
                ?: "null", command.matId, EventType.ERROR_EVENT, mapOf("error" to error))

        return when (command.type) {
            CommandType.INIT_MAT_STATE_COMMAND -> {
                val matState = MatState(command.correlationId!!, command.matId!!, command.payload?.get("periodId").toString(), command.competitionId)
                if (command.payload?.containsKey("matFights") == true) {
                    val fights = mapper.convertValue(command.payload?.get("matFights"), Array<FightDescription>::class.java)
                    val newState = matState.setFights(fights)
                    listOf(createEvent(EventType.MAT_STATE_INITIALIZED, mapOf("matState" to newState)))
                } else {
                    listOf(createEvent(EventType.MAT_STATE_INITIALIZED, mapOf("matState" to matState)))
                }
            }
            else -> listOf(createErrorEvent("Unknown command: ${command.type}"))
        }
    }

}