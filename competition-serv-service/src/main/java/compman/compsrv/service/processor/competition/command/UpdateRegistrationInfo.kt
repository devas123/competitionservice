package compman.compsrv.service.processor.competition.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.Payload
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.UpdateRegistrationInfoPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationInfoUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_COMMAND_EXECUTORS)
class UpdateRegistrationInfo(mapper: ObjectMapper, validators: List<PayloadValidator>) : ICommandExecutor<Competition>,
    ValidatedCommandExecutor<Competition>(mapper, validators) {
    override fun execute(
        entity: Competition?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Competition> = entity?.let {
        executeValidated<UpdateRegistrationInfoPayload>(command) { payload, com ->
            entity to entity.process(payload, com, AbstractAggregateService.Companion::createEvent)
        }.unwrap(command)
    } ?: error(Constants.COMPETITION_NOT_FOUND)

    fun Competition.process(
        payload: UpdateRegistrationInfoPayload,
        command: CommandDTO,
        createEvent: (CommandDTO, EventType, Payload?) -> EventDTO
    ): List<EventDTO> {
        if (!payload.registrationInfo?.id.isNullOrBlank() && registrationInfo.id == payload.registrationInfo.id) {
            return listOf(
                createEvent(
                    command,
                    EventType.REGISTRATION_INFO_UPDATED,
                    RegistrationInfoUpdatedPayload(payload.registrationInfo)
                )
            )
        } else {
            throw IllegalArgumentException("Registration info not provided, or does not exist for id ${payload.registrationInfo?.id}")
        }
    }

    override val commandType: CommandType
        get() = CommandType.UPDATE_REGISTRATION_INFO_COMMAND
}