package compman.compsrv.service

import compman.compsrv.kafka.streams.LeaderProcessStreams
import compman.compsrv.kafka.topics.CompetitionServiceTopics
import compman.compsrv.model.competition.CompetitionDashboardState
import compman.compsrv.model.es.commands.Command
import compman.compsrv.model.es.commands.CommandType
import compman.compsrv.model.es.events.EventHolder
import compman.compsrv.model.es.events.EventType
import compman.compsrv.model.schedule.DashboardPeriod
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

@Component
class DashboardStateService(private val stateQueryService: StateQueryService) : ICommandProcessingService<CompetitionDashboardState, Command, EventHolder> {

    override fun apply(event: EventHolder, state: CompetitionDashboardState?): Pair<CompetitionDashboardState?, List<EventHolder>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun process(command: Command, state: CompetitionDashboardState?): List<EventHolder> {
        fun createEvent(type: EventType, payload: Map<String, Any?>) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId, command.matId
                ?: "null", type, payload)

        fun createErrorEvent(error: String) = EventHolder(command.correlationId!!, command.competitionId, command.categoryId, command.matId
                ?: "null", EventType.ERROR_EVENT, mapOf("error" to error))

        return when (command.type) {
            CommandType.CHECK_MAT_OBSOLETE -> {
                if (state == null) {
                    listOf(createEvent(EventType.MAT_DELETED, command.payload ?: emptyMap()))
                } else {
                    val periodId = command.payload?.get("periodId").toString()
                    val period = state.periods.find { it.id == periodId }
                    if (period == null) {
                        listOf(createEvent(EventType.MAT_DELETED, command.payload ?: emptyMap()))
                    } else if (!period.matIds.contains(command.matId)) {
                        listOf(createEvent(EventType.MAT_DELETED, command.payload ?: emptyMap()))
                    } else {
                        emptyList()
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
                            val newState = CompetitionDashboardState(command.correlationId!!, competitionProperties.competitionId, dbPeriods.toSet())
                            newState
                            listOf(createEvent(EventType.DASHBOARD_STATE_INITIALIZED, mapOf("state" to newState))) + dbPeriods.map {
                                createEvent(EventType.DASHBOARD_PERIOD_INITIALIZED, mapOf("period" to it))
                                        .setMetadata(mapOf(LeaderProcessStreams.ROUTING_METADATA_KEY to CompetitionServiceTopics.MAT_GLOBAL_INTERNAL_EVENTS_TOPIC_NAME))
                            }
                        } else {
                            listOf(createErrorEvent("Periods are missing."))
                        }
                    } else {
                        listOf(createErrorEvent("Could not find competition properties"))
                    }
                } else {
                    listOf(createErrorEvent("State already initialized: $state"))
                }
            }
            CommandType.DELETE_DASHBOARD_STATE_COMMAND -> {
                val deletedMats = state?.periods?.map {
                    createEvent(EventType.DASHBOARD_PERIOD_DELETED, mapOf("period" to it))
                            .setMetadata(mapOf(LeaderProcessStreams.ROUTING_METADATA_KEY to CompetitionServiceTopics.MAT_GLOBAL_INTERNAL_EVENTS_TOPIC_NAME))
                } ?: emptyList()
                (listOf(createEvent(EventType.DASHBOARD_STATE_DELETED, emptyMap())) + deletedMats)
            }
            CommandType.INIT_PERIOD_COMMAND -> {
                val periodId = command.payload?.get("periodId")
                val period = state?.periods?.find { it.id == periodId }
                if (period != null) {
                    state.upsertPeriod(period.setActive(true))
                    listOf(createEvent(EventType.DASHBOARD_PERIOD_INITIALIZED, mapOf("period" to period)))
                } else {
                    listOf(createErrorEvent("Did not find period with id $periodId"))
                }
            }
            CommandType.DELETE_PERIOD_COMMAND -> {
                val periodId = command.payload?.get("periodId")
                val period = state?.periods?.find { it.id == periodId }
                if (period != null) {
                    state.deletePeriod(period.id)
                    listOf(createEvent(EventType.DASHBOARD_PERIOD_DELETED, mapOf("period" to period)))
                } else {
                    listOf(createErrorEvent("Did not find period with id $periodId"))
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
                            state.upsertPeriod(newPeriod)
                            listOf(createEvent(EventType.UNDISPATCHED_MAT_ADDED, (command.payload
                                    ?: emptyMap()) + mapOf("matId" to matId)).setMatId(matId))
                        } else {
                            listOf(createErrorEvent("Did not find period with id $periodId"))
                        }
                    } else {
                        listOf(createErrorEvent("Period ID is null"))
                    }
                } else {
                    listOf(createErrorEvent("state is null"))
                }
            }
            else -> listOf(createErrorEvent("Unknown command: ${command.type}"))
        }
    }

}