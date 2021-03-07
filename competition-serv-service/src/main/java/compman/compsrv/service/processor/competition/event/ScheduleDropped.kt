package compman.compsrv.service.processor.competition.event

import compman.compsrv.aggregate.Competition
import compman.compsrv.config.COMPETITION_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(COMPETITION_EVENT_HANDLERS)
class ScheduleDropped : IEventHandler<Competition> {
    override fun applyEvent(
        aggregate: Competition?,
        event: EventDTO,
        rocksDBOperations: DBOperations
    ): Competition? = aggregate?.let {
        aggregate.properties.schedulePublished = false
        aggregate.categories.forEach { catId ->
            val cat = rocksDBOperations.getCategory(catId, true)
            cat.stages.values.flatMap { it.fights.toList() }.forEach { id ->
                val fight = rocksDBOperations.getFight(id, true)
                fight.startTime = null
                fight.invalid = false
                fight.mat = null
                fight.numberOnMat = null
                fight.period = null
                rocksDBOperations.putFight(fight)
            }
            rocksDBOperations.putCategory(cat)
        }
        return aggregate.copy(periods = emptyArray(), mats = emptyArray())
    }

    override val eventType: EventType
        get() = EventType.SCHEDULE_DROPPED
}