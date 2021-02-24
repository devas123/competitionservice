package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.FightStartTimeUpdatedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class FightsStartTimeUpdated(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
        aggregate: Category?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Category? = aggregate?.let {
        executeValidated<FightStartTimeUpdatedPayload, Category>(event) { payload, _ ->
            val competition = rocksDBOperations.getCompetition(event.competitionId)

            aggregate.fightStartTimeUpdated(payload, competition.mats)
        }.unwrap(event)
    } ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.fightStartTimeUpdated(
        payload: FightStartTimeUpdatedPayload,
        mats: Array<MatDescriptionDTO>
    ): Category {
        for (newFight in payload.newFights) {
            fightsMap[newFight.fightId]?.let {
                it.invalid = newFight.invalid
                it.mat = mats.find { m -> m.id == newFight.matId }
                it.period = newFight.periodId
                it.startTime = newFight.startTime
                it.numberOnMat = newFight.numberOnMat
            }
        }
        return this
    }

    override val eventType: EventType
        get() = EventType.FIGHTS_START_TIME_UPDATED
}