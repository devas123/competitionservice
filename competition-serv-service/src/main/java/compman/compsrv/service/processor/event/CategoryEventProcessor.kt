package compman.compsrv.service.processor.event

import com.compmanager.compservice.jooq.tables.daos.CategoryDescriptorDao
import com.compmanager.compservice.jooq.tables.daos.CompetitionPropertiesDao
import com.compmanager.compservice.jooq.tables.daos.CompetitorDao
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.mapping.toPojo
import compman.compsrv.model.commands.payload.CategoryRegistrationStatusChangePayload
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.JooqRepository
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component


@Component
class CategoryEventProcessor(mapper: ObjectMapper,
                             validators: List<PayloadValidator>,
                             private val competitionPropertiesDao: CompetitionPropertiesDao,
                             private val categoryDescriptorCrudRepository: CategoryDescriptorDao,
                             private val competitorCrudRepository: CompetitorDao,
                             private val jooqRepository: JooqRepository) : AbstractEventProcessor(mapper, validators) {
    override fun affectedEvents(): Set<EventType> {
        return setOf(
                EventType.COMPETITOR_ADDED,
                EventType.COMPETITOR_REMOVED,
                EventType.COMPETITOR_UPDATED,
                EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
                EventType.BRACKETS_GENERATED,
                EventType.FIGHTS_ADDED_TO_STAGE,
                EventType.FIGHTS_START_TIME_UPDATED,
                EventType.CATEGORY_DELETED,
                EventType.STAGE_STATUS_UPDATED,
                EventType.CATEGORY_BRACKETS_DROPPED,
                EventType.CATEGORY_ADDED,
                EventType.CATEGORY_REGISTRATION_STATUS_CHANGED,
                EventType.COMPETITOR_CATEGORY_CHANGED)
    }

    override fun applyEvent(event: EventDTO): List<EventDTO> {
        return when (event.type) {
            EventType.STAGE_STATUS_UPDATED -> appluStageStatusUpdated(event)
            EventType.CATEGORY_REGISTRATION_STATUS_CHANGED -> applyCategoryRegistrationStatusChanged(event)
            EventType.COMPETITOR_ADDED -> applyCompetitorAddedEvent(event)
            EventType.COMPETITOR_REMOVED -> applyCompetitorRemovedEvent(event)
            EventType.COMPETITOR_UPDATED -> applyCompetitorUpdatedEvent(event)
            EventType.COMPETITOR_CATEGORY_CHANGED -> applyCompetitorCategoryChangedEvent(event)
            EventType.FIGHTS_EDITOR_CHANGE_APPLIED -> applyCompetitorMovedEvent(event)
            EventType.BRACKETS_GENERATED -> applyBracketsGeneratedEvent(event)
            EventType.FIGHTS_ADDED_TO_STAGE -> applyFightsAddedToStage(event)
            EventType.FIGHTS_START_TIME_UPDATED -> applyFighStartTimeUpdatedEvent(event)
            EventType.CATEGORY_DELETED -> applyCategoryStateDeletedEvent(event)
            EventType.CATEGORY_BRACKETS_DROPPED -> applyCategoryBracketsDroppedEvent(event)
            EventType.CATEGORY_ADDED -> applyCategoryAddedEvent(event)
            else -> {
                log.warn("Unknown event type: ${event.type}")
                throw EventApplyingException("No event handler for event ${event.type}", event)
            }
        }
    }

    private fun appluStageStatusUpdated(event: EventDTO) = executeValidated(event, StageStatusUpdatedPayload::class.java) { payload, _ ->
        jooqRepository.updateStageStatus(payload.stageId, payload.status)
    }

    private fun applyFightsAddedToStage(event: EventDTO): List<EventDTO> = executeValidated(event, FightsAddedToStagePayload::class.java) { payload, _ ->
        jooqRepository.saveFights(payload.fights.toList())
    }

    private fun applyCategoryBracketsDroppedEvent(event: EventDTO): List<EventDTO> {
        jooqRepository.dropStages(event.categoryId)
        return emptyList()
    }

    private fun applyCompetitorCategoryChangedEvent(event: EventDTO): List<EventDTO> = executeValidated(event, ChangeCompetitorCategoryPayload::class.java) { payload, _ ->
        jooqRepository.changeCompetitorCategories(payload.fighterId, listOf(payload.oldCategoryId), listOf(payload.newCategoryId))
    }

    private fun applyCompetitorUpdatedEvent(event: EventDTO): List<EventDTO> = executeValidated(event, CompetitorUpdatedPayload::class.java) { payload, _ ->
        val competitor = payload.fighter
        if (competitor != null) {
            competitorCrudRepository.update(competitor.toPojo())
        } else {
            throw EventApplyingException("Competitor is null or such competitor does not exist.", event)
        }
    }


    private fun applyCompetitorAddedEvent(event: EventDTO): List<EventDTO> = executeValidated(event, CompetitorAddedPayload::class.java) { payload, _ ->
        val competitor = payload.fighter
        if (competitor != null && !competitor.id.isNullOrBlank() && !competitor.categories.isNullOrEmpty()) {
            log.info("Adding competitor: ${competitor.id} to competition ${event.competitionId} and category ${competitor.categories}")
            jooqRepository.saveCompetitors(listOf(competitor))
            jooqRepository.changeCompetitorCategories(competitor.id, emptyList(), competitor.categories.toList())
        } else {
            throw EventApplyingException("No competitor in the event payload: $event", event)
        }
    }

    private fun applyCategoryRegistrationStatusChanged(event: EventDTO): List<EventDTO> = executeValidated(event, CategoryRegistrationStatusChangePayload::class.java) { payload, _ ->
        val newStatus = payload.isNewStatus
        val category = categoryDescriptorCrudRepository.findById(event.categoryId)
        category.registrationOpen = newStatus
        categoryDescriptorCrudRepository.update(category)
    }


    private fun applyCompetitorRemovedEvent(event: EventDTO): List<EventDTO> = executeValidated(event, CompetitorRemovedPayload::class.java) { payload, _ ->
        val competitorId = payload.fighterId
        if (competitorId != null) {
            competitorCrudRepository.deleteById(competitorId)
        } else {
            throw EventApplyingException("Competitor id is null.", event)
        }
    }

    private fun applyCompetitorMovedEvent(event: EventDTO) = executeValidated(event, FightEditorChangesAppliedPayload::class.java) { payload, _ ->
        val removals = payload.removedFighids.orEmpty()
        val updates = payload.updates.orEmpty()
        val newFights = payload.newFights.orEmpty()
        jooqRepository.deleteFightsByIds(removals.toList())
        jooqRepository.saveFights(newFights.toList())
        jooqRepository.updateFightsStatusAndCompScores(updates.toList())
    }

    private fun applyFighStartTimeUpdatedEvent(event: EventDTO) = executeValidated(event, FightStartTimeUpdatedPayload::class.java) { payload, _ ->
        val newFights = payload.newFights
        if (!newFights.isNullOrEmpty()) {
            jooqRepository.batchUpdateFightStartTimesMatPeriodNumber(newFights.filterNotNull())
        } else {
            throw EventApplyingException("Fights are null.", event)
        }
    }


    private fun applyBracketsGeneratedEvent(event: EventDTO) = executeValidated(event, BracketsGeneratedPayload::class.java) { payload, _ ->
        val stages = payload.stages
        val categoryId = event.categoryId
        if (stages != null && !categoryId.isNullOrBlank()) {
            jooqRepository.doInTransaction {
                jooqRepository.saveStages(stages.mapIndexedNotNull { index, stage ->
                    stage.setStageOrder(index)
                })
                jooqRepository.saveInputDescriptors(stages.mapNotNull { it.inputDescriptor })
                jooqRepository.saveResultDescriptors(stages.mapNotNull { it.stageResultDescriptor?.let { srd -> it.id to srd } })
                jooqRepository.saveGroupDescriptors(stages.map {
                    it.id to (it.groupDescriptors?.filter { gd -> !gd.id.isNullOrBlank() }.orEmpty())
                })
            }
        } else {
            throw EventApplyingException("Fights are null or empty or category ID is empty.", event)
        }
    }

    private fun applyCategoryAddedEvent(event: EventDTO) = executeValidated(event, CategoryAddedPayload::class.java) { payload, _ ->
        val c = payload.categoryState
        if (c != null && event.categoryId != null && competitionPropertiesDao.existsById(event.competitionId) && c.category != null
                && !c.category.restrictions.isNullOrEmpty()) {
            log.info("Adding category: ${event.categoryId} to competition ${event.competitionId}")
            jooqRepository.saveCategoryDescriptor(c.category, event.competitionId)
        } else {
            throw EventApplyingException("event did not contain category state.", event)
        }
    }

    private fun applyCategoryStateDeletedEvent(event: EventDTO): List<EventDTO> {
        return if (!event.categoryId.isNullOrBlank()) {
            categoryDescriptorCrudRepository.deleteById(event.categoryId)
            emptyList()
        } else {
            throw EventApplyingException("Category ID is null.", event)
        }
    }
}