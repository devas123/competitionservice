package compman.compsrv.service.processor.command

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competitor
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.commands.payload.CreateFakeCompetitorsPayload
import compman.compsrv.model.commands.payload.UpdateCompetitorPayload
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorAddedPayload
import compman.compsrv.model.events.payload.CompetitorRemovedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightsService
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component


@Component
class CompetitorAggregateService constructor(mapper: ObjectMapper,
                                             validators: List<PayloadValidator>) : AbstractAggregateService<Competitor>(mapper, validators) {

    private val doAddCompetitor: CommandExecutor<Competitor> = { c, _, command ->
        val competitor = mapper.convertValue(command.payload, CompetitorDTO::class.java) //TODO: increment category's number of competitors too.
        listOf(c to c.process(competitor, command, this::createEvent))
    }

    private val doRemoveCompetitor: CommandExecutor<Competitor> = { competitor, rocksDb, command ->
        if (rocksDb.fightsCount(competitor.competitorDTO.categories.orEmpty().toList(), command.competitorId) <= 0) {
            listOf(competitor to listOf(createEvent(command, EventType.COMPETITOR_REMOVED, CompetitorRemovedPayload(command.competitorId))))
        } else {
            throw IllegalStateException("There are already fights generated for the competitor. Please remove the fights first.")
        }
    }

    private val doUpdateCompetitor: CommandExecutor<Competitor> = { competitor, _, command ->
        executeValidated(command, UpdateCompetitorPayload::class.java) { payload, com ->
            listOf(competitor to competitor.process(payload, com, this::createEvent))
        }.unwrap(command)
    }

    private val doChangeCompetitorCategory: CommandExecutor<Competitor> = { competitor, _, command -> //TODO: update number of competitors in categories
        executeValidated(command, ChangeCompetitorCategoryPayload::class.java) { payload, com ->
            listOf(competitor to competitor.process(payload, com, this::createEvent))
        }.unwrap(command)
    }


    private val doCreateFakeCompetitors: CommandExecutor<Competitor> = { _, _, command ->
        val payload = mapper.convertValue(command.payload, CreateFakeCompetitorsPayload::class.java)
        val numberOfCompetitors = payload?.numberOfCompetitors ?: 50
        val numberOfAcademies = payload?.numberOfAcademies ?: 30
        val fakeCompetitors = FightsService.generateRandomCompetitorsForCategory(numberOfCompetitors, numberOfAcademies, command.categoryId, command.competitionId!!)
        fakeCompetitors.map {
            Competitor(it) to listOf(createEvent(command, EventType.COMPETITOR_ADDED, CompetitorAddedPayload(it)))
        }
    }


    override val commandsToHandlers: Map<CommandType, CommandExecutor<Competitor>> = mapOf(
            CommandType.ADD_COMPETITOR_COMMAND to doAddCompetitor,
            CommandType.REMOVE_COMPETITOR_COMMAND to doRemoveCompetitor,
            CommandType.UPDATE_COMPETITOR_COMMAND to doUpdateCompetitor,
            CommandType.CHANGE_COMPETITOR_CATEGORY_COMMAND to doChangeCompetitorCategory,
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND to doCreateFakeCompetitors)


    override fun getAggregate(command: CommandDTO, rocksDBOperations: DBOperations): Either<List<Competitor>, Competitor> {
        return when (command.type) {
            CommandType.ADD_COMPETITOR_COMMAND -> {
                Competitor(CompetitorDTO()).right()
            }
            CommandType.CREATE_FAKE_COMPETITORS_COMMAND -> {
                emptyList<Competitor>().left()
            }
            else -> {
                rocksDBOperations.getCompetitor(command.competitorId).right()
            }
        }
    }

    override fun getAggregate(event: EventDTO, rocksDBOperations: DBOperations): Competitor = rocksDBOperations.getCompetitor(event.competitorId, true)

}