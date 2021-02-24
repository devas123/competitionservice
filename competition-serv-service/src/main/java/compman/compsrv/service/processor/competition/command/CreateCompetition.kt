package compman.compsrv.service.processor.competition.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.Payload
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.CreateCompetitionPayload
import compman.compsrv.model.dto.competition.CompetitionStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitionCreatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.AbstractAggregateService
import compman.compsrv.service.processor.AggregateWithEvents
import compman.compsrv.service.processor.ICommandExecutor
import compman.compsrv.service.processor.ValidatedCommandExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId

@Component
@Qualifier(COMPETITION_COMMAND_EXECUTORS)
class CreateCompetition(mapper: ObjectMapper, validators: List<PayloadValidator>) : ICommandExecutor<Competition>,
    ValidatedCommandExecutor<Competition>(mapper, validators) {
    override fun execute(
        entity: Competition?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Competition> = entity?.let {
        executeValidated<CreateCompetitionPayload>(command) { payload, com ->
            entity to entity.process(payload, com, AbstractAggregateService.Companion::createEvent)
        }.unwrap(command)
    } ?: error(Constants.COMPETITION_NOT_FOUND)

    fun Competition.process(
        payload: CreateCompetitionPayload,
        com: CommandDTO,
        createEvent: (command: CommandDTO, eventType: EventType, payload: Payload?) -> EventDTO
    ): List<EventDTO> {
        val newProperties = payload.properties
        return if (newProperties != null) {
            if (!newProperties.competitionName.isNullOrBlank()) {
                if (newProperties.startDate == null) {
                    newProperties.startDate = Instant.now()
                }
                if (newProperties.endDate == null) {
                    newProperties.endDate = Instant.now()
                }
                if (newProperties.creationTimestamp == null) {
                    newProperties.creationTimestamp = Instant.now()
                }
                if (newProperties.status == null) {
                    newProperties.status = CompetitionStatus.CREATED
                }
                if (newProperties.timeZone.isNullOrBlank() || newProperties.timeZone == "null") {
                    newProperties.timeZone = ZoneId.systemDefault().id
                }
                listOf(
                    createEvent(
                        com, EventType.COMPETITION_CREATED, CompetitionCreatedPayload(
                            newProperties.setId(com.competitionId), payload.reginfo?.setId(com.competitionId)
                        )
                    )
                )
            } else {
                throw IllegalArgumentException("Competition name is empty")
            }
        } else {
            throw IllegalArgumentException("Cannot create competition, no properties provided")
        }
    }


    override val commandType: CommandType
        get() = CommandType.CREATE_COMPETITION_COMMAND
}