package compman.compsrv.kafka.streams

import compman.compsrv.json.ObjectMapperFactory
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.competition.FightDescription
import compman.compsrv.model.competition.MatState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.validators.MatCommandsValidatorRegistry
import org.slf4j.LoggerFactory

class MatCommandExecutorTransformer(stateStoreName: String,
                                    private val validators: MatCommandsValidatorRegistry) : StateForwardingValueTransformer<MatState>(stateStoreName, CompetitionServiceTopics.MAT_STATE_CHANGELOG_TOPIC_NAME) {
    override fun getStateKey(command: Command?) = command?.matId!!
    override fun updateCorrelationId(currentState: MatState, command: Command): MatState = currentState.copy(correlationId = command.correlatioId!!)

    companion object {
        private val log = LoggerFactory.getLogger(MatCommandExecutorTransformer::class.java)
    }
    private val mapper = ObjectMapperFactory.createObjectMapper()


    override fun doTransform(currentState: MatState?, command: Command?): Triple<String?, MatState?, List<EventHolder>?> {
        fun createEvent(type: EventType, payload: Map<String, Any?>?) =
                EventHolder(command!!.correlatioId!!, command.competitionId, command.categoryId, command.matId, type, payload)
        return try {
            log.info("Executing a mat command: $command, partition: ${context.partition()}, offset: ${context.offset()}")
            if (command?.matId != null) {
                val matId = command.matId!!
                val validationErrors = canExecuteCommand(currentState, command)
                if (validationErrors.isEmpty()) {
                    val (newState, events) = executeCommand(command, currentState)
                    Triple(matId, newState, events)
                } else {
                    log.warn("Not executed, command validation failed.  \nCommand: $command. \nState: $currentState. \nPartition: ${context.partition()}. \nOffset: ${context.offset()}, errors: $validationErrors")
                    Triple(null, null, listOf(createEvent(EventType.ERROR_EVENT, mapOf("errors" to validationErrors))))
                }
            } else {
                log.warn("Did not execute because either command is null (${command == null}) or competition id is wrong: ${command?.competitionId}")
                Triple(null, null, listOf(createEvent(EventType.ERROR_EVENT,
                        mapOf("error" to "Did not execute command $command because either it is null (${command == null}) or competition id is wrong: ${command?.competitionId}"))))
            }
        } catch (e: Throwable) {
            log.error("Error while processing command: $command", e)
            Triple(null, null, listOf(createEvent(EventType.ERROR_EVENT, mapOf("error" to "${e.message}"))))
        }
    }

    private fun executeCommand(command: Command, state: MatState?): Pair<MatState?, List<EventHolder>> {
        fun createEvent(type: EventType, payload: Map<String, Any?>) = EventHolder(command.correlatioId!!, command.competitionId, command.categoryId
                ?: "null", command.matId, type, payload)

        fun createErrorEvent(error: String) = EventHolder(command.correlatioId!!, command.competitionId, command.categoryId
                ?: "null", command.matId, EventType.ERROR_EVENT, mapOf("error" to error))

        return when (command.type) {
            CommandType.INIT_MAT_STATE_COMMAND -> {
                val matState = MatState(command.correlatioId!!, command.matId!!, command.payload?.get("periodId").toString(), command.competitionId)
                if (command.payload?.containsKey("matFights") == true) {
                    val fights = mapper.convertValue(command.payload?.get("matFights"), Array<FightDescription>::class.java)
                    val newState = matState.setFights(fights)
                    newState to listOf(createEvent(EventType.MAT_STATE_INITIALIZED, mapOf("matState" to newState)))
                } else {
                    matState to listOf(createEvent(EventType.MAT_STATE_INITIALIZED, mapOf("matState" to matState)))
                }
            }
            else -> state to listOf(createErrorEvent("Unknown command: ${command.type}"))
        }
    }

    private fun canExecuteCommand(state: MatState?, command: Command?): List<String> {
        /*(state == null || state.eventOffset < context.offset()) &&*/
        return validators.validate(command, state)
    }
}