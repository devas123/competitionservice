package compman.compsrv.service.processor.competition

import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.RegistrationInfoDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.CommandExecutor
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.IEventHandler
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
class CompetitionAggregateService(
    @Qualifier(COMPETITION_COMMAND_EXECUTORS)
    commandExecutors: List<ICommandExecutor<Competition>>,
    @Qualifier(COMPETITION_EVENT_HANDLERS)
    eventHandlers: List<IEventHandler<Competition>>
) : AbstractAggregateService<Competition>() {


    override val commandsToHandlers: Map<CommandType, CommandExecutor<Competition>> =
        commandExecutors.groupBy { it.commandType }.mapValues { it ->
            it.value.first()
        }.mapValues { e -> { cmp: Competition?, ops: DBOperations, c: CommandDTO -> e.value.execute(cmp, ops, c) } }


    override fun getAggregate(command: CommandDTO, rocksDBOperations: DBOperations): Competition? {
        return when (command.type) {
            CommandType.CREATE_COMPETITION_COMMAND -> {
                Competition(
                    id = command.competitionId,
                    properties = CompetitionPropertiesDTO().setId(command.competitionId),
                    registrationInfo = RegistrationInfoDTO().setId(command.competitionId)
                )
            }
            else -> {
                kotlin.runCatching {
                    rocksDBOperations.getCompetition(command.competitionId)
                }.getOrLogAndNull()
            }
        }
    }

    override fun getAggregate(event: EventDTO, rocksDBOperations: DBOperations): Competition? =
        when (event.type) {
            EventType.COMPETITION_CREATED -> {
                Competition(
                    id = event.competitionId,
                    properties = CompetitionPropertiesDTO().setId(event.competitionId),
                    registrationInfo = RegistrationInfoDTO().setId(event.competitionId)
                )
            }
            else -> {
                kotlin.runCatching { rocksDBOperations.getCompetition(event.competitionId, true) }
                    .getOrLogAndNull()
            }
        }

    override fun saveAggregate(aggregate: Competition?, rocksDBOperations: DBOperations): Competition? {
        return aggregate?.also { rocksDBOperations.putCompetition(aggregate) }
    }

    override fun isAggregateDeleted(event: EventDTO): Boolean {
        return event.type == EventType.COMPETITION_DELETED
    }


    override val eventsToProcessors: Map<EventType, IEventHandler<Competition>> =
        eventHandlers.groupBy { it.eventType }.mapValues { e -> e.value.first() }

}