package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.DashboardFightOrderChangedPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class DashboardFightOrderChanged(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>
): IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
            aggregate: Category,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Category {
        return executeValidated<DashboardFightOrderChangedPayload, Category>(event) { payload, _ ->
            aggregate.dashboardFightOrderChanged(payload)
        }.unwrap(event)
    }

    fun Category.dashboardFightOrderChanged(payload: DashboardFightOrderChangedPayload): Category {
        if (payload.newMatId != payload.currentMatId) {
            //if mats are different
            for (f in fights) {
                if (f.id != payload.fightId && f.mat.id == payload.currentMatId && f.numberOnMat != null && f.numberOnMat >= payload.currentOrderOnMat) {
                    //first reduce numbers on the current mat
                    f.numberOnMat = f.numberOnMat - 1
                    f.startTime = f.startTime.minus(payload.fightDuration.toLong(), ChronoUnit.MINUTES)
                } else if (f.id != payload.fightId && f.mat.id == payload.newMatId && f.numberOnMat != null && f.numberOnMat >= payload.newOrderOnMat) {
                    f.numberOnMat = f.numberOnMat + 1
                    f.startTime = f.startTime.plus(payload.fightDuration.toLong(), ChronoUnit.MINUTES)
                } else if (f.id == payload.fightId) {
                    f.mat = f.mat.setId(payload.newMatId)
                    f.numberOnMat = payload.newOrderOnMat
                }
            }
        } else {
            //mats are the same
            for (f in fights) {
                if (f.id != payload.fightId && f.mat.id == payload.currentMatId && f.numberOnMat != null
                        && f.numberOnMat >= min(payload.currentOrderOnMat, payload.newOrderOnMat) &&
                        f.numberOnMat <= max(payload.currentOrderOnMat, payload.newOrderOnMat)
                ) {
                    //first reduce numbers on the current mat
                    if (payload.currentOrderOnMat > payload.newOrderOnMat) {
                        f.numberOnMat = f.numberOnMat + 1
                        f.startTime = f.startTime.plus(payload.fightDuration.toLong(), ChronoUnit.MINUTES)
                    } else {
                        //update fight
                        f.numberOnMat = f.numberOnMat - 1
                        f.startTime = f.startTime.minus(payload.fightDuration.toLong(), ChronoUnit.MINUTES)
                    }
                } else if (f.id == payload.fightId) {
                    f.mat = f.mat.setId(payload.newMatId)
                    f.numberOnMat = payload.newOrderOnMat
                }
            }
        }
        return this
    }


    override val eventType: EventType
        get() = EventType.DASHBOARD_FIGHT_ORDER_CHANGED
}