package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.FightCompetitorsAssignedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class FightCompetitorsAssigned(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
        aggregate: Category?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Category? = aggregate?.let {
        executeValidated<FightCompetitorsAssignedPayload, Category>(event) { payload, _ ->
            aggregate.fightCompetitorsAssigned(payload, rocksDBOperations)
        }.unwrap(event)
    } ?: error(Constants.CATEGORY_NOT_FOUND)

    fun Category.fightCompetitorsAssigned(payload: FightCompetitorsAssignedPayload, rocksDBOperations: DBOperations): Category {
        val assignments = payload.assignments
        for (assignment in assignments) {
            val fromFight = rocksDBOperations.getFight(assignment.fromFightId)
            val toFight =   rocksDBOperations.getFight(assignment.toFightId)
            toFight.scores?.find { it.parentFightId == fromFight.id }?.let {
                it.competitorId = assignment.competitorId
                it.parentReferenceType = it.parentReferenceType ?: assignment.referenceType
            } ?: error("No target score for ${assignment.fromFightId} in fight ${assignment.toFightId}")
            rocksDBOperations.putFight(toFight)
        }
        return this
    }


    override val eventType: EventType
        get() = EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED
}