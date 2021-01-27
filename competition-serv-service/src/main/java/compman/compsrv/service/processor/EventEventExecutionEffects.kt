package compman.compsrv.service.processor

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component

@Component
class EventEventExecutionEffects(val mapper: ObjectMapper,
                                 val validators: List<PayloadValidator>,
                                 val fightsGenerateService: FightServiceFactory)  {
/*
    companion object {
        val log: Logger = LoggerFactory.getLogger(EventEventExecutionEffects::class.java)
    }

    private inline fun <reified T : Payload> executeWithEffects(event: EventDTO, payloadClass: Class<T>,
                                                                crossinline logic: (payload: T, event: EventDTO) -> List<EventDTO>): List<EventDTO> {
        val payload = mapper.getPayloadAs(event, payloadClass)!!
        return kotlin.runCatching {
            PayloadValidationRules
                    .accumulateErrors { payload.validate(event, validators).fix() }
                    .map { logic(payload, event) }
                    .fold({ it.map { p -> mapper.createErrorEvent(event, p) }.all }, { it })
        }
                .getOrElse {
                    log.error("Error during event execution: $event", it)
                    listOf(mapper.createErrorEvent(event, "Error during event processing: ${it.message}"))
                }
    }


    override fun effects(event: EventDTO): List<EventDTO> {
        return when (event.type) {
            EventType.SCHEDULE_GENERATED -> executeWithEffects(event, ScheduleGeneratedPayload::class.java) { _, e ->
                listOf(mapper.createEffect(e, EventType.COMPETITION_PROPERTIES_UPDATED,
                        CompetitionPropertiesUpdatedPayload().setProperties(mapOf("schedulePublished" to true))))
            }
            EventType.SCHEDULE_DROPPED -> listOf(mapper.createEffect(event, EventType.COMPETITION_PROPERTIES_UPDATED,
                    CompetitionPropertiesUpdatedPayload().setProperties(mapOf("schedulePublished" to false))))
            EventType.DASHBOARD_STAGE_RESULT_SET -> executeWithEffects(event, StageResultSetPayload::class.java) { payload, e ->
                val selectors = selectorDao.fetchByApplyToStageId(payload.stageId)?.groupBy { it.stageInputId }.orEmpty()
                selectors.map { (p, _) ->
                    val stage = jooqRepository.fetchStageById(e.competitionId, p).block(Duration.ofMillis(300))
                            ?: throw IllegalStateException("Cannot get stage with id $p")

                    val propagatedCompetitors = findPropagatedCompetitors(stage, payload, e)
                    val propagatedStageFights = jooqRepository.fetchFightsByStageId(e.competitionId, p).collectList().block(Duration.ofMillis(300))
                            ?: throw IllegalStateException("No fights found for stage $p")

                    val competitorIdsToFightIds = fightsGenerateService
                            .distributeCompetitors(propagatedCompetitors, propagatedStageFights, stage.bracketType)
                            .fold(emptyList<CompetitorAssignmentDescriptor>()) { acc, f ->
                                val newPairs = f.scores?.mapNotNull {
                                    it.competitorId?.let { c ->
                                        CompetitorAssignmentDescriptor().setCompetitorId(c)
                                                .setToFightId(f.id)
                                    }
                                }.orEmpty()
                                acc + newPairs
                            }
                    mapper.createEffect(e, EventType.COMPETITORS_PROPAGATED_TO_STAGE, CompetitorsPropagatedToStagePayload()
                            .setStageId(p)
                            .setPropagations(competitorIdsToFightIds))
                }
            }
            EventType.COMPETITORS_PROPAGATED_TO_STAGE -> executeWithEffects(event, CompetitorsPropagatedToStagePayload::class.java) { payload, e ->
                val stage = stageDescriptorDao.findById(payload.stageId)
                if (stage.waitForPrevious) {
                    listOf(mapper.createEffect(e, EventType.STAGE_STATUS_UPDATED, StageStatusUpdatedPayload().setStageId(payload.stageId).setStatus(StageStatus.WAITING_FOR_APPROVAL)))
                } else {
                    listOf(mapper.createEffect(e, EventType.STAGE_STATUS_UPDATED, StageStatusUpdatedPayload().setStageId(payload.stageId).setStatus(StageStatus.IN_PROGRESS)))
                }
            }
            else -> emptyList()
        }
    }

    private fun findPropagatedCompetitors(stage: StageDescriptorDTO, payload: StageResultSetPayload, e: EventDTO): List<CompetitorDTO> {
        val propagatedCompetitorIds = fightsGenerateService.applyStageInputDescriptorToResultsAndFights(
                stage.bracketType,
                stage.inputDescriptor, payload.stageId,
                { id -> fightResultOptionDao.fetchByStageId(id).map { it.toDTO() } },
                { id -> competitorStageResultDao.fetchByStageId(id).map { it.toDTO() } },
                { id ->
                    jooqRepository.fetchFightsByStageId(e.competitionId, id).collectList().block(Duration.ofMillis(300))
                            .orEmpty()
                })
        return competitorDao.fetchById(*propagatedCompetitorIds.toTypedArray()).map { it.toDTO(arrayOf(e.categoryId)) }
    }
*/
}