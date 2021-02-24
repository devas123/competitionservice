package compman.compsrv.service.processor.competition.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.Payload
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.AddRegistrationPeriodPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationPeriodAddedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_COMMAND_EXECUTORS)
class AddRegistrationPeriod(mapper: ObjectMapper, validators: List<PayloadValidator>) : ICommandExecutor<Competition>,
    ValidatedCommandExecutor<Competition>(mapper, validators) {
    override fun execute(
        entity: Competition?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Competition> = entity?.let {
        executeValidated<AddRegistrationPeriodPayload>(command) { payload, com ->
            entity to entity.process(payload, com, AbstractAggregateService.Companion::createEvent)
        }.unwrap(command)
    } ?: error(Constants.COMPETITION_NOT_FOUND)


    fun Competition.process(
        payload: AddRegistrationPeriodPayload,
        com: CommandDTO,
        createEvent: (CommandDTO, EventType, Payload?) -> EventDTO
    ): List<EventDTO> {
        return if (payload.period != null) {
            val periodId = IDGenerator.hashString("${com.competitionId}/${payload.period.name}")
            if (registrationInfo.registrationPeriods?.any { it.id == periodId } != true) {
                listOf(
                    createEvent(
                        com,
                        EventType.REGISTRATION_PERIOD_ADDED,
                        RegistrationPeriodAddedPayload(payload.period.setId(periodId))
                    )
                )
            } else {
                throw IllegalArgumentException("Period with id ${payload.period.id} already exists.")
            }
        } else {
            throw IllegalArgumentException("Period is not specified or competition id is missing.")
        }
    }


    override val commandType: CommandType
        get() = CommandType.ADD_REGISTRATION_PERIOD_COMMAND
}