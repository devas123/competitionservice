package compman.compsrv.service.processor.category.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.DashboardFightOrderChangePayload
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.DashboardFightOrderChangedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.*
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import kotlin.math.max

@Component
@Qualifier(CATEGORY_COMMAND_EXECUTORS)
class ChangeFightOrder(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : ICommandExecutor<Category>, ValidatedCommandExecutor<Category>(mapper, validators) {
    override fun execute(
        entity: Category?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Category> =
        entity?.let {
            executeValidated<DashboardFightOrderChangePayload>(command) { payload, _ ->
                entity to entity.process(payload, command, AbstractAggregateService.Companion::createEvent)
            }.unwrap(command)
        } ?: error(Constants.CATEGORY_NOT_FOUND)


    private fun Category.process(payload: DashboardFightOrderChangePayload, c: CommandDTO, createEvent: CreateEvent): List<EventDTO> {
        val newOrderOnMat = max(payload.newOrderOnMat, 0)
        val fight = fights.first { it.id == payload.fightId }
        val periodId = fight.period
        return when (fight.status) {
            FightStatus.IN_PROGRESS, FightStatus.FINISHED -> {
                throw IllegalArgumentException("Cannot move fight that is finished or in progress.")
            }
            else -> {
                listOf(
                    createEvent(
                        c, EventType.DASHBOARD_FIGHT_ORDER_CHANGED, DashboardFightOrderChangedPayload()
                            .setFightId(fight.id)
                            .setNewOrderOnMat(newOrderOnMat)
                            .setPeriodId(periodId)
                            .setFightDuration(fight.duration)
                            .setCurrentMatId(fight.mat.id)
                            .setCurrentOrderOnMat(fight.numberOnMat)
                            .setNewMatId(payload.newMatId)
                    )
                )
            }
        }
    }


    override val commandType: CommandType
        get() = CommandType.DASHBOARD_FIGHT_ORDER_CHANGE_COMMAND


}