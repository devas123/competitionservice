package compman.compsrv.service.processor.category.command

import arrow.core.curry
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.UpdateStageStatusPayload
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.StageStatusUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.processor.*
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.applyConditionalUpdate
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_COMMAND_EXECUTORS)
class UpdateStageStatus(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : ICommandExecutor<Category>, ValidatedCommandExecutor<Category>(mapper, validators) {
    override fun execute(
        entity: Category?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Category> =
        entity?.let {
            executeValidated<UpdateStageStatusPayload>(command) { payload, c ->
                entity to entity.process(payload, c, AbstractAggregateService.Companion::createEvent)
            }.unwrap(command)
        } ?: error(Constants.CATEGORY_NOT_FOUND)

    private fun Category.process(
        payload: UpdateStageStatusPayload,
        c: CommandDTO,
        createEvent: CreateEvent
    ): List<EventDTO> {
        val stage = stages.first { it.id == payload.stageId }
        val stageFights = fights.filter { it.stageId == stage.id }
        val version = version()
        return when (payload.status) {
            StageStatus.FINISHED, StageStatus.IN_PROGRESS -> listOf(
                createEvent(
                    c,
                    EventType.STAGE_STATUS_UPDATED,
                    StageStatusUpdatedPayload(payload.stageId, payload.status)
                )
            )
            StageStatus.WAITING_FOR_APPROVAL, StageStatus.APPROVED -> {
                val dirtyStageFights = stageFights.applyConditionalUpdate({ it.status == FightStatus.UNCOMPLETABLE },
                    { it.setStatus(FightStatus.PENDING) })
                val markedStageFights =
                    FightsService.markAndProcessUncompletableFights(dirtyStageFights, payload.status) { id ->
                        (dirtyStageFights.firstOrNull { it.id == id }
                            ?: stageFights.firstOrNull { it.id == id })?.scores?.toList()
                    }
                listOf(
                    createEvent(
                        c,
                        EventType.STAGE_STATUS_UPDATED,
                        StageStatusUpdatedPayload(payload.stageId, payload.status)
                    )
                ) +
                        Category.createFightEditorChangesAppliedEvents(
                            c,
                            emptyList(),
                            markedStageFights,
                            emptyList(),
                            createEvent
                        ).map(this::enrichWithVersionAndNumber.curry()(version))
            }
            StageStatus.WAITING_FOR_COMPETITORS -> {
                val dirtyStageFights = stageFights.applyConditionalUpdate({ it.status == FightStatus.UNCOMPLETABLE },
                    { it.setStatus(FightStatus.PENDING) })
                listOf(
                    createEvent(
                        c,
                        EventType.STAGE_STATUS_UPDATED,
                        StageStatusUpdatedPayload(payload.stageId, payload.status)
                    )
                ) +
                        Category.createFightEditorChangesAppliedEvents(
                            c,
                            emptyList(),
                            dirtyStageFights,
                            emptyList(),
                            createEvent
                        ).map(this::enrichWithVersionAndNumber.curry()(version))
            }
            else -> throw IllegalArgumentException("Wrong status: ${payload.status}.")
        }
    }


    override val commandType: CommandType
        get() = CommandType.UPDATE_STAGE_STATUS_COMMAND


}