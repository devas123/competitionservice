package compman.compsrv.service.processor.category.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_EVENT_HANDLERS
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.ScoreDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorsPropagatedToStagePayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.processor.IEventHandler
import compman.compsrv.service.processor.ValidatedEventExecutor
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_EVENT_HANDLERS)
class CompetitorsPropagatedToStage(
        mapper: ObjectMapper,
        validators: List<PayloadValidator>
) : IEventHandler<Category>, ValidatedEventExecutor<Category>(mapper, validators) {
    override fun applyEvent(
            aggregate: Category,
            event: EventDTO,
            rocksDBOperations: DBOperations
    ): Category {
        return executeValidated<CompetitorsPropagatedToStagePayload, Category>(event) { payload, _ ->
            aggregate.competitorsPropagatedToStage(payload)
        }.unwrap(event)
    }

    fun Category.competitorsPropagatedToStage(payload: CompetitorsPropagatedToStagePayload): Category {
        val propagations = payload.propagations
        propagations
                .groupBy { it.toFightId }
                .entries.forEach { entry ->
                    val compScores = entry.value.mapIndexed { ind, p ->
                        CompScoreDTO().setCompetitorId(p.competitorId)
                                .setParentFightId(p.fromFightId)
                                .setOrder(ind)
                                .setParentReferenceType(FightReferenceType.PROPAGATED)
                                .setScore(ScoreDTO().setAdvantages(0).setPoints(0).setPenalties(0))
                    }
                    fightsMap[entry.key]?.let { f ->
                        f.scores = compScores.toTypedArray() + f.scores.orEmpty()
                    }
                }
        return this
    }


    override val eventType: EventType
        get() = EventType.COMPETITORS_PROPAGATED_TO_STAGE
}