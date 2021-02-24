package compman.compsrv.service.processor.competition.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.DeleteRegistrationPeriodPayload
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationPeriodDeletedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_COMMAND_EXECUTORS)
class DeleteRegistrationPeriod(mapper: ObjectMapper, validators: List<PayloadValidator>) :
    ICommandExecutor<Competition>, ValidatedCommandExecutor<Competition>(mapper, validators) {
    override fun execute(
        entity: Competition?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Competition> = executeValidated<DeleteRegistrationPeriodPayload>(command) { payload, _ ->
        entity to listOf(
            AbstractAggregateService.createEvent(
                command,
                EventType.REGISTRATION_PERIOD_DELETED,
                RegistrationPeriodDeletedPayload(payload.periodId)
            )
        )
    }.unwrap(command)

    override val commandType: CommandType
        get() = CommandType.DELETE_REGISTRATION_PERIOD_COMMAND
}