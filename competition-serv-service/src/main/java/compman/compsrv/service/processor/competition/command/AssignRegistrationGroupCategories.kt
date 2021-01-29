package compman.compsrv.service.processor.competition.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.AssignRegistrationGroupCategoriesPayload
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationGroupCategoriesAssignedPayload
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
class AssignRegistrationGroupCategories(mapper: ObjectMapper, validators: List<PayloadValidator>) : ICommandExecutor<Competition>, ValidatedCommandExecutor<Competition>(mapper, validators) {
    override fun execute(
            entity: Competition,
            dbOperations: DBOperations,
            command: CommandDTO
    ): AggregateWithEvents<Competition> = executeValidated<AssignRegistrationGroupCategoriesPayload>(command) { payload, com ->
        entity to entity.process(payload, com, AbstractAggregateService.Companion::createEvent)
    }.unwrap(command)

    fun Competition.process(payload: AssignRegistrationGroupCategoriesPayload, command: CommandDTO, createEvent: (CommandDTO, EventType, Payload?) -> EventDTO): List<EventDTO> {
        return if (periodExists(payload.periodId)) {
            if (groupExists(payload.groupId)) {
                listOf(createEvent(command, EventType.REGISTRATION_GROUP_CATEGORIES_ASSIGNED, RegistrationGroupCategoriesAssignedPayload(payload.periodId, payload.groupId, payload.categories)))
            } else {
                throw IllegalArgumentException("Unknown group id: ${payload.groupId}")
            }
        } else {
            throw IllegalArgumentException("Unknown period id: ${payload.periodId}")
        }
    }


    override val commandType: CommandType
        get() = CommandType.ASSIGN_REGISTRATION_GROUP_CATEGORIES_COMMAND
}