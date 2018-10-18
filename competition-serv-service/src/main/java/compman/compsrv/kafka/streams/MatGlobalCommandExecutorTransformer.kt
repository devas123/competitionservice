package compman.compsrv.kafka.streams

import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.competition.CompetitionDashboardState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.schedule.DashboardPeriod
import compman.compsrv.service.StateQueryService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class MatGlobalCommandExecutorTransformer(stateStoreName: String,
                                          private val stateQueryService: StateQueryService) : StateForwardingValueTransformer<CompetitionDashboardState>(stateStoreName, CompetitionServiceTopics.DASHBOARD_STATE_CHANGELOG_TOPIC_NAME) {
    override fun getStateKey(command: Command?) = command?.competitionId!!
    override fun updateCorrelationId(currentState: CompetitionDashboardState, command: Command): CompetitionDashboardState = currentState.copy(correlationId = command.correlatioId)

    companion object {
        private val log = LoggerFactory.getLogger(MatGlobalCommandExecutorTransformer::class.java)
    }

    override fun doTransform(currentState: CompetitionDashboardState?, command: Command?): Triple<String?, CompetitionDashboardState?, List<EventHolder>?> {
        fun createEvent(type: EventType, payload: Map<String, Any?>) = EventHolder(command!!.correlatioId, command.competitionId, command.categoryId
                ?: "null", command.matId, type, payload)

        fun createErrorEvent(error: String) = EventHolder(command!!.correlatioId, command.competitionId, command.categoryId
                ?: "null", command.matId, EventType.ERROR_EVENT, mapOf("error" to error))
        return try {
            log.info("Executing a mat command: $command, partition: ${context.partition()}, offset: ${context.offset()}")
            if (command?.competitionId != null) {
                val competitionId = command.competitionId
                val validationErrors = canExecuteCommand(currentState, command)
                if (validationErrors.isEmpty()) {
                    val (newState, events) = executeCommand(command, currentState)
                    Triple(competitionId, newState, events)
                } else {
                    log.warn("Not executed, command validation failed.  \nCommand: $command. \nState: $currentState. \nPartition: ${context.partition()}. \nOffset: ${context.offset()}, errors: $validationErrors")
                    Triple(null, null, listOf(createEvent(EventType.ERROR_EVENT, mapOf("errors" to validationErrors))))
                }
            } else {
                log.warn("Did not execute because either command is null (${command == null}) or competition id is wrong: ${command?.competitionId}")
                Triple(null, null, listOf(createErrorEvent("Did not execute command $command because either it is null (${command == null}) or competition id is wrong: ${command?.competitionId}")))
            }
        } catch (e: Throwable) {
            log.error("Error while processing command: $command", e)
            Triple(null, null, listOf(createErrorEvent(e.message ?: e::class.java.canonicalName)))
        }
    }

    private fun executeCommand(command: Command, state: CompetitionDashboardState?): Pair<CompetitionDashboardState?, List<EventHolder>> {
        fun createEvent(type: EventType, payload: Map<String, Any?>) = EventHolder(command.correlatioId, command.competitionId, command.categoryId, command.matId
                ?: "null", type, payload)

        fun createErrorEvent(error: String) = EventHolder(command.correlatioId, command.competitionId, command.categoryId, command.matId
                ?: "null", EventType.ERROR_EVENT, mapOf("error" to error))

        return when (command.type) {
            CommandType.CHECK_MAT_OBSOLETE -> {
                if (state == null) {
                    state to listOf(createEvent(EventType.MAT_DELETED, command.payload ?: emptyMap()))
                } else {
                    val periodId = command.payload?.get("periodId").toString()
                    val period = state.periods.find { it.id == periodId }
                    if (period == null) {
                        state to listOf(createEvent(EventType.MAT_DELETED, command.payload ?: emptyMap()))
                    } else if (!period.matIds.contains(command.matId)) {
                        state to listOf(createEvent(EventType.MAT_DELETED, command.payload ?: emptyMap()))
                    } else {
                        state to emptyList()
                    }
                }
            }
            CommandType.INIT_DASHBOARD_STATE_COMMAND -> {
                val competitionProperties = stateQueryService.getCompetitionProperties(competitionId = command.competitionId)
                if (state == null) {
                    if (competitionProperties != null) {
                        val periods = competitionProperties.schedule?.periods
                        if (periods?.isEmpty() == false) {
                            val dbPeriods = periods.map {
                                val periodId = it.id
                                val mats = (0 until it.numberOfMats).map { i -> "$periodId-mat-$i" }
                                DashboardPeriod(periodId, it.name, mats.toTypedArray(), Date.from(Instant.from(DateTimeFormatter.ISO_INSTANT.parse(it.startTime))), false)
                            }
                            val newState = CompetitionDashboardState(command.correlatioId, competitionProperties.competitionId, dbPeriods.toSet(), competitionProperties)
                            newState to listOf(createEvent(EventType.DASHBOARD_STATE_INITIALIZED, mapOf("state" to newState))) + dbPeriods.map {
                                createEvent(EventType.PERIOD_INITIALIZED, mapOf("period" to it))
                                        .setMetadata(mapOf(LeaderProcessStreams.ROUTING_METADATA_KEY to CompetitionServiceTopics.MATS_GLOBAL_INTERNAL_EVENTS_TOPIC_NAME))
                            }
                        } else {
                            state to listOf(createErrorEvent("Periods are missing."))
                        }
                    } else {
                        state to listOf(createErrorEvent("Could not find competition properties"))
                    }
                } else {
                    state to listOf(createErrorEvent("State already initialized: $state"))
                }
            }
            CommandType.DELETE_DASHBOARD_STATE_COMMAND -> {
                val deletedMats = state?.periods?.map {
                    createEvent(EventType.DASHBOARD_PERIOD_DELETED, mapOf("period" to it))
                            .setMetadata(mapOf(LeaderProcessStreams.ROUTING_METADATA_KEY to CompetitionServiceTopics.MATS_GLOBAL_INTERNAL_EVENTS_TOPIC_NAME))
                } ?: emptyList()
                null to (listOf(createEvent(EventType.DASHBOARD_STATE_DELETED, emptyMap())) + deletedMats)
            }
            CommandType.INIT_PERIOD_COMMAND -> {
                val periodId = command.payload?.get("periodId")
                val period = state?.periods?.find { it.id == periodId }
                if (period != null) {
                    state.upsertPeriod(period.setActive(true)) to listOf(createEvent(EventType.PERIOD_INITIALIZED, mapOf("period" to period)))
                } else {
                    state to listOf(createErrorEvent("Did not find period with id $periodId"))
                }
            }
            CommandType.DELETE_PERIOD_COMMAND -> {
                val periodId = command.payload?.get("periodId")
                val period = state?.periods?.find { it.id == periodId }
                if (period != null) {
                    state.deletePeriod(period.id) to listOf(createEvent(EventType.DASHBOARD_PERIOD_DELETED, mapOf("period" to period)))
                } else {
                    state to listOf(createErrorEvent("Did not find period with id $periodId"))
                }
            }
            CommandType.ADD_UNDISPATCHED_MAT_COMMAND -> {
                if (state != null) {
                    val periodId = command.payload?.get("periodId")?.toString()
                    if (periodId != null) {
                        val period = state.periods.find { it.id == periodId }
                        if (period != null) {
                            val matId = "$periodId-mat-undispatched"
                            val newPeriod = period.addMat(matId)
                            state.upsertPeriod(newPeriod) to listOf(createEvent(EventType.UNDISPATCHED_MAT_ADDED, (command.payload
                                    ?: emptyMap()) + mapOf("matId" to matId)).setMatId(matId))
                        } else {
                            state to listOf(createErrorEvent("Did not find period with id $periodId"))
                        }
                    } else {
                        state to listOf(createErrorEvent("Period ID is null"))
                    }
                } else {
                    state to listOf(createErrorEvent("state is null"))
                }
            }
            else -> state to listOf(createErrorEvent("Unknown command: ${command.type}"))
        }
    }

    private fun canExecuteCommand(state: CompetitionDashboardState?, command: Command?): List<String> {
        /*(state == null || state.eventOffset < context.offset()) &&*/
        return emptyList()
    }
}