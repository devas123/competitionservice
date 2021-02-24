package compman.compsrv.service.processor.competition.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.Payload
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.GenerateSchedulePayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.FightStartTimeUpdatedPayload
import compman.compsrv.model.events.payload.ScheduleGeneratedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.service.schedule.ScheduleService
import compman.compsrv.service.schedule.StageGraph
import compman.compsrv.util.Constants
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.toMonoOrEmpty
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
@Qualifier(COMPETITION_COMMAND_EXECUTORS)
class GenerateSchedule(
    private val scheduleService: ScheduleService,
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : ICommandExecutor<Competition>, ValidatedCommandExecutor<Competition>(mapper, validators) {
    override fun execute(
        entity: Competition?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Competition> = entity?.let {
        executeValidated<GenerateSchedulePayload>(command) { payload, com ->
            entity to entity.process(
                payload,
                com,
                scheduleService,
                getAllBrackets(com.competitionId, dbOperations),
                getNumberOfCompetitorsByCategoryId(com.competitionId, dbOperations),
                AbstractAggregateService.Companion::createEvent
            )
        }.unwrap(command)
    } ?: error(Constants.COMPETITION_NOT_FOUND)

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

    fun Competition.process(
        payload: GenerateSchedulePayload,
        com: CommandDTO,
        scheduleService: ScheduleService,
        allBrackets: Mono<StageGraph>,
        competitorNumbersByCategoryIds: Map<String, Int>,
        createEvent: (command: CommandDTO, eventType: EventType, payload: Payload?) -> EventDTO
    ): List<EventDTO> {
        val periods = payload.periods?.toList()
        val mats = payload.mats?.map {
            it.setId(it.id ?: IDGenerator.createMatId(it.periodId))
        }!!
        val compProps = properties
        val categories = periods?.flatMap {
            it.scheduleEntries?.toList().orEmpty()
        }?.flatMap { it.categoryIds?.toList().orEmpty() }?.distinct()
        val missingCategories = categories?.fold(emptyList<String>(), { acc, cat ->
            if (categories.contains(cat)) {
                acc
            } else {
                acc + cat
            }
        })
        return if (!compProps.schedulePublished && !periods.isNullOrEmpty()) {
            if (missingCategories.isNullOrEmpty()) {
                val tuple = scheduleService.generateSchedule(
                    com.competitionId, periods, mats,
                    allBrackets,
                    compProps.timeZone,
                    competitorNumbersByCategoryIds
                )
                val schedule = tuple.a
                val newFights = tuple.b
                val fightStartTimeUpdatedEvents = newFights.chunked(100) { list ->
                    val fightStartTimeUpdatedPayload = FightStartTimeUpdatedPayload().setNewFights(list.toTypedArray())
                    createEvent(com, EventType.FIGHTS_START_TIME_UPDATED, fightStartTimeUpdatedPayload)
                }
                listOf(
                    createEvent(
                        com,
                        EventType.SCHEDULE_GENERATED,
                        ScheduleGeneratedPayload(schedule)
                    )
                ) + fightStartTimeUpdatedEvents
            } else {
                throw IllegalArgumentException("Categories $missingCategories are unknown")
            }
        } else {
            throw IllegalArgumentException("Could not find competition with ID: ${com.competitionId}")
        }
    }


    override val commandType: CommandType
        get() = CommandType.ADD_REGISTRATION_GROUP_COMMAND
}