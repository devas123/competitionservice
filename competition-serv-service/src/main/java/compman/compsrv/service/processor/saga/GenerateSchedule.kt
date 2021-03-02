package compman.compsrv.service.processor.saga

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.AbstractAggregate
import compman.compsrv.aggregate.Competition
import compman.compsrv.errors.SagaExecutionError
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
import compman.compsrv.service.processor.DelegatingAggregateService
import compman.compsrv.service.processor.ISagaExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.service.schedule.ScheduleService
import compman.compsrv.service.schedule.StageGraph
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component

@Component
class GenerateSchedule(
    private val scheduleService: ScheduleService,
    mapper: ObjectMapper,
    validators: List<PayloadValidator>,
    override val delegatingAggregateService: DelegatingAggregateService
) : ISagaExecutor, ValidatedCommandExecutor<AbstractAggregate>(mapper, validators) {
    override fun createSaga(
        dbOperations: DBOperations,
        command: CommandDTO
    ): Either<SagaExecutionError, SagaStep<List<EventDTO>>> =
        createSaga<GenerateSchedulePayload>(command) { payload, com ->
            val entity = dbOperations.getCompetition(command.competitionId)
            entity.process(
                payload,
                com,
                scheduleService,
                getAllBrackets(com.competitionId, dbOperations),
                getNumberOfCompetitorsByCategoryId(com.competitionId, dbOperations),
                AbstractAggregateService.Companion::createEvent
            )
        }


    private fun getAllBrackets(competitionId: String, rocksDBOperations: DBOperations): StageGraph {
        val competition = rocksDBOperations.getCompetition(competitionId, true)
        val categories = rocksDBOperations.getCategories(competition.categories.toList(), true)
        val stages = categories.flatMap { it.stages.toList() }
        val fights = categories.flatMap { it.fights.toList() }
        return StageGraph(stages, fights)
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
        allBrackets: StageGraph,
        competitorNumbersByCategoryIds: Map<String, Int>,
        createEvent: (command: CommandDTO, eventType: EventType, payload: Payload?) -> EventDTO
    ): SagaStep<List<EventDTO>> {
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
                scheduleService.generateSchedule(
                    com.competitionId, periods, mats,
                    allBrackets,
                    compProps.timeZone,
                    competitorNumbersByCategoryIds
                ).let { tuple ->
                    val schedule = tuple.a
                    val newFights = tuple.b
                    val fightStartTimeUpdatedEvents = newFights.groupBy { it.fightCategoryId }.mapValues { (key, list) ->
                        val fightStartTimeUpdatedPayload =
                            FightStartTimeUpdatedPayload().setNewFights(list.toTypedArray())
                        applyEvent(Unit.left(), createEvent(com, EventType.FIGHTS_START_TIME_UPDATED, fightStartTimeUpdatedPayload).apply { categoryId = key })
                    }.values
                    applyEvent(this.right(),
                        createEvent(
                            com,
                            EventType.SCHEDULE_GENERATED,
                            ScheduleGeneratedPayload(schedule)
                        )) +
                            fightStartTimeUpdatedEvents.reduce { acc, free -> acc + free }
                }
            } else {
                throw IllegalArgumentException("Categories $missingCategories are unknown")
            }
        } else {
            throw IllegalArgumentException("Could not find competition with ID: ${com.competitionId}")
        }
    }


    override val commandType: CommandType
        get() = CommandType.GENERATE_SCHEDULE_COMMAND
}