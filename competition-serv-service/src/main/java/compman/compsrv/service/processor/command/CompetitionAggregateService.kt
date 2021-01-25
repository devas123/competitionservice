package compman.compsrv.service.processor.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.cluster.ClusterOperations
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.*
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.dto.competition.RegistrationInfoDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.schedule.ScheduleService
import compman.compsrv.service.schedule.StageGraph
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.toMonoOrEmpty
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class CompetitionAggregateService(
    private val scheduleService: ScheduleService,
    private val clusterOperations: ClusterOperations,
    validators: List<PayloadValidator>,
    mapper: ObjectMapper
) : AbstractAggregateService<Competition>(mapper, validators) {

    private fun getAllBrackets(competitionId: String, rocksDBOperations: DBOperations): Mono<StageGraph> {
        val competition = rocksDBOperations.getCompetition(competitionId, true)
        val categories = rocksDBOperations.getCategories(competition.categories.toList(), true)
        val stages = categories.flatMap { it.stages.toList() }
        val fights = categories.flatMap { it.fights.toList() }
        return StageGraph(stages, fights).toMonoOrEmpty()
    }

    private fun getNumberOfCompetitorsByCategoryId(
        competitionId: String,
        rocksDBOperations: DBOperations
    ): Map<String, Int> {
        val competition = rocksDBOperations.getCompetition(competitionId, true)
        val categories = rocksDBOperations.getCategories(competition.categories.toList(), true)
        return categories.map { it.id to it.numberOfCompetitors }.toMap()
    }


    private val sendProcessingInfoCommand: CommandExecutor<Competition> = { competition, _, command ->
        competition to clusterOperations.createProcessingInfoEvents(command.correlationId, setOf(command.competitionId))
            .toList()
    }

    private val updateRegistrationInfoCommand: CommandExecutor<Competition> = { competition, _, command ->
        executeValidated<UpdateRegistrationInfoPayload>(command) { payload, com ->
            competition to competition.process(payload, com, Companion::createEvent)
        }.unwrap(command)
    }

    private val deleteRegistrationPeriod: CommandExecutor<Competition> = { competition, _, command ->
        executeValidated<DeleteRegistrationPeriodPayload>(command) { payload, _ ->
            competition to listOf(
                createEvent(
                    command,
                    EventType.REGISTRATION_PERIOD_DELETED,
                    RegistrationPeriodDeletedPayload(payload.periodId)
                )
            )
        }.unwrap(command)
    }
    private val dropAllBrackets: CommandExecutor<Competition> = { competition, _, command ->
        if (competition.properties.bracketsPublished != true) {
            competition to competition.categories.map { cat ->
                createEvent(
                    command,
                    EventType.CATEGORY_BRACKETS_DROPPED,
                    command.payload
                ).apply { categoryId = cat }
            }
        } else {
            throw IllegalArgumentException("Brackets already published")
        }
    }
    private val dropSchedule: CommandExecutor<Competition> = { competition, _, command ->
        if (competition.properties.schedulePublished != true) {
            competition to listOf(createEvent(command, EventType.SCHEDULE_DROPPED, command.payload))
        } else {
            throw IllegalArgumentException("Schedule already published")
        }
    }
    private val deleteRegistrationGroup: CommandExecutor<Competition> = { competition, _, command ->
        executeValidated<DeleteRegistrationGroupPayload>(command) { payload, com ->
            competition to competition.process(payload, com, Companion::createEvent)
        }.unwrap(command)
    }
    private val addRegistrationGroup: CommandExecutor<Competition> = { competition, _, command ->
        executeValidated<AddRegistrationGroupPayload>(command) { payload, com ->
            competition to competition.process(payload, com, Companion::createEvent)
        }.unwrap(command)
    }
    private val addRegistrationPeriod: CommandExecutor<Competition> = { competition, _, command ->
        executeValidated<AddRegistrationPeriodPayload>(command) { payload, com ->
            competition to competition.process(payload, com, Companion::createEvent)
        }.unwrap(command)
    }

    private val assignRegistrationGroupCategories: CommandExecutor<Competition> = { competition, _, command ->
        executeValidated<AssignRegistrationGroupCategoriesPayload>(command) { payload, com ->
            competition to competition.process(payload, com, Companion::createEvent)
        }.unwrap(command)
    }

    private val createCompetition: CommandExecutor<Competition> = { competition, _, command ->
        executeValidated<CreateCompetitionPayload>(command) { payload, com ->
            competition to competition.process(payload, com, Companion::createEvent)
        }.unwrap(command)
    }

    private val generateSchedule: CommandExecutor<Competition> = { competition, db, command ->
        executeValidated<GenerateSchedulePayload>(command) { payload, com ->
            competition to competition.process(
                payload,
                com,
                scheduleService,
                getAllBrackets(com.competitionId, db),
                getNumberOfCompetitorsByCategoryId(com.competitionId, db),
                Companion::createEvent
            )
        }.unwrap(command)
    }

    private val updateProperties: CommandExecutor<Competition> = { competition, _, command ->
        competition to listOf(createEvent(command, EventType.COMPETITION_PROPERTIES_UPDATED, command.payload!!))
    }
    private val startCompetition: CommandExecutor<Competition> = { competition, _, command ->
        competition to listOf(
            createEvent(
                command,
                EventType.COMPETITION_STARTED,
                CompetitionStatusUpdatedPayload(CompetitionStatus.STARTED)
            )
        )
    }
    private val stopCompetition: CommandExecutor<Competition> = { competition, _, command ->
        competition to listOf(
            createEvent(
                command,
                EventType.COMPETITION_STOPPED,
                CompetitionStatusUpdatedPayload(CompetitionStatus.STOPPED)
            )
        )
    }
    private val publishCompetition: CommandExecutor<Competition> = { competition, _, command ->
        competition to listOf(
            createEvent(
                command,
                EventType.COMPETITION_PUBLISHED,
                CompetitionStatusUpdatedPayload(CompetitionStatus.PUBLISHED)
            )
        )
    }
    private val unPublishCompetition: CommandExecutor<Competition> = { competition, _, command ->
        competition to listOf(
            createEvent(
                command,
                EventType.COMPETITION_PUBLISHED,
                CompetitionStatusUpdatedPayload(CompetitionStatus.UNPUBLISHED)
            )
        )
    }
    private val deleteCompetition: CommandExecutor<Competition> = { competition, _, command ->
        competition to listOf(createEvent(command, EventType.COMPETITION_DELETED, null))
    }


    override val commandsToHandlers: Map<CommandType, CommandExecutor<Competition>> = mapOf(
        CommandType.ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND to assignRegistrationGroupCategories,
        CommandType.UPDATE_REGISTRATION_INFO_COMMAND to updateRegistrationInfoCommand,
        CommandType.INTERNAL_SEND_PROCESSING_INFO_COMMAND to sendProcessingInfoCommand,
        CommandType.DELETE_REGISTRATION_PERIOD_COMMAND to deleteRegistrationPeriod,
        CommandType.DELETE_REGISTRATION_GROUP_COMMAND to deleteRegistrationGroup,
        CommandType.ADD_REGISTRATION_GROUP_COMMAND to addRegistrationGroup,
        CommandType.ADD_REGISTRATION_PERIOD_COMMAND to addRegistrationPeriod,
        CommandType.CREATE_COMPETITION_COMMAND to createCompetition,
        CommandType.DROP_ALL_BRACKETS_COMMAND to dropAllBrackets,
        CommandType.DROP_SCHEDULE_COMMAND to dropSchedule,
        CommandType.GENERATE_SCHEDULE_COMMAND to generateSchedule,
        CommandType.UPDATE_COMPETITION_PROPERTIES_COMMAND to updateProperties,
        CommandType.START_COMPETITION_COMMAND to startCompetition,
        CommandType.STOP_COMPETITION_COMMAND to stopCompetition,
        CommandType.PUBLISH_COMPETITION_COMMAND to publishCompetition,
        CommandType.UNPUBLISH_COMPETITION_COMMAND to unPublishCompetition,
        CommandType.DELETE_COMPETITION_COMMAND to deleteCompetition
    )


    override fun getAggregate(command: CommandDTO, rocksDBOperations: DBOperations): Competition {
        return when (command.type) {
            CommandType.CREATE_COMPETITION_COMMAND -> {
                Competition(
                    id = command.competitionId,
                    properties = CompetitionPropertiesDTO().setId(command.competitionId),
                    registrationInfo = RegistrationInfoDTO().setId(command.competitionId)
                )
            }
            else -> {
                rocksDBOperations.getCompetition(command.competitionId)
            }
        }
    }

    override fun getAggregate(event: EventDTO, rocksDBOperations: DBOperations): Competition =
        rocksDBOperations.getCompetition(event.competitionId, true)

    override fun Payload.accept(aggregate: Competition, event: EventDTO): Competition {
        return when (this) {
            is RegistrationGroupCategoriesAssignedPayload ->
                aggregate.registrationGroupCategoriesAssigned(this)
            is RegistrationInfoUpdatedPayload ->
                aggregate.registrationInfoUpdated(this)
            else -> throw EventApplyingException("Payload ${this.javaClass} not supported.", event)
        }
    }

    override fun saveAggregate(aggregate: Competition, rocksDBOperations: DBOperations): Competition {
        TODO("Not yet implemented")
    }

}