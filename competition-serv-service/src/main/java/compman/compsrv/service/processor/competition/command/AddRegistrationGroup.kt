package compman.compsrv.service.processor.competition.command

import arrow.core.Either
import arrow.core.extensions.either.monad.monad
import arrow.core.extensions.list.foldable.foldM
import arrow.core.fix
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_COMMAND_EXECUTORS
import compman.compsrv.model.Payload
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.AddRegistrationGroupPayload
import compman.compsrv.model.dto.competition.RegistrationGroupDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.RegistrationGroupAddedPayload
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
class AddRegistrationGroup(mapper: ObjectMapper, validators: List<PayloadValidator>) : ICommandExecutor<Competition>,
    ValidatedCommandExecutor<Competition>(mapper, validators) {
    override fun execute(
        entity: Competition?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Competition> = entity?.let {
        executeValidated<AddRegistrationGroupPayload>(command) { payload, com ->
            entity to entity.process(payload, com, AbstractAggregateService.Companion::createEvent)
        }.unwrap(command)
    } ?: error(Constants.COMPETITION_NOT_FOUND)


    fun Competition.process(
        payload: AddRegistrationGroupPayload,
        com: CommandDTO,
        createEvent: (CommandDTO, EventType, Payload?) -> EventDTO
    ): List<EventDTO> {
        return if (!payload.periodId.isNullOrBlank() && !payload.groups.isNullOrEmpty()) {
            val groupsList = payload.groups.toList()
            val k = groupsList.foldM(Either.monad(), emptyList<RegistrationGroupDTO>()) { acc, group ->
                if (!group?.displayName.isNullOrBlank() && !group?.registrationInfoId.isNullOrBlank()) {
                    val groupId = IDGenerator.hashString("${group.registrationInfoId}/${group.displayName}")
                    val regInfoId = group.registrationInfoId ?: com.competitionId
                    val periodGroups =
                        registrationInfo.registrationGroups?.filter { it.registrationPeriodIds.contains(payload.periodId) }
                    val defaultGroup = group?.defaultGroup?.let {
                        if (it) {
                            registrationInfo.registrationGroups?.find { group -> group.id == groupId }
                        } else {
                            null
                        }
                    }
                    if (defaultGroup != null) {
                        Either.left("There is already a default group for competition ${com.competitionId} with different id: ${defaultGroup.displayName ?: "<Unknown>"}, ${defaultGroup.id}")
                    } else {
                        if (registrationInfo.registrationPeriods?.any { it.id == payload.periodId } == true) {
                            if (periodGroups?.any { it.id == groupId } != true) {
                                Either.right(acc + group.setId(groupId).setRegistrationInfoId(regInfoId))
                            } else {
                                Either.left("Group with id $groupId already exists")
                            }
                        } else {
                            Either.left("Cannot find period with ID: ${payload.periodId}")
                        }
                    }
                } else {
                    Either.left("Group name is not specified ${group?.displayName}.")
                }
            }.fix()
            k.fold(
                {
                    throw IllegalArgumentException(it)
                },
                {
                    listOf(
                        createEvent(
                            com,
                            EventType.REGISTRATION_GROUP_ADDED,
                            RegistrationGroupAddedPayload(payload.periodId, it.toTypedArray())
                        )
                    )
                })
        } else {
            throw IllegalArgumentException("Period Id is not specified or no groups to add")
        }
    }


    override val commandType: CommandType
        get() = CommandType.ADD_REGISTRATION_GROUP_COMMAND
}