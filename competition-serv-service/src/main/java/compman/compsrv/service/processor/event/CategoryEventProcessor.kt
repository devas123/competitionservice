package compman.compsrv.service.processor.event

import com.compmanager.compservice.jooq.tables.daos.*
import com.compmanager.compservice.jooq.tables.pojos.Competitor
import com.compmanager.model.payment.RegistrationStatus
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.payload.CategoryRegistrationStatusChangePayload
import compman.compsrv.model.commands.payload.ChangeCompetitorCategoryPayload
import compman.compsrv.model.commands.payload.JsonPatch
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.*
import compman.compsrv.util.getPayloadFromString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.sql.Timestamp
import kotlin.math.min


fun CompetitorDTO.toCompetitor() =
        Competitor().also { cmp ->
            cmp.id = this.id
            cmp.academyId = this.academy?.id
            cmp.academyName = this.academy?.name
            cmp.birthDate = this.birthDate?.let { Timestamp.from(it) }
            cmp.email = this.email
            cmp.firstName = this.firstName
            cmp.lastName = this.lastName
            cmp.competitionId = this.competitionId
            cmp.promo = this.promo
            cmp.registrationStatus = this.registrationStatus?.let { RegistrationStatus.valueOf(it).ordinal }
            cmp.userId = this.userId
        }

@Component
class CategoryEventProcessor(private val mapper: ObjectMapper,
                             private val competitionPropertiesDao: CompetitionPropertiesDao,
                             private val categoryDescriptorCrudRepository: CategoryDescriptorDao,
                             private val competitorCrudRepository: CompetitorDao,
                             private val jdbcRepository: JdbcRepository,
                             private val jooqQueries: JooqQueries,
                             private val stageDescriptorCrudRepository: StageDescriptorDao) : IEventProcessor {
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
                EventType.CATEGORY_BRACKETS_DROPPED,
                EventType.CATEGORY_ADDED,
                EventType.CATEGORY_REGISTRATION_STATUS_CHANGED,
                EventType.COMPETITOR_CATEGORY_CHANGED)
    }

    override fun applyEvent(event: EventDTO): List<EventDTO> {
        when (event.type) {
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
            }
        }
        return listOf(event)
    }

    private fun applyFightsAddedToStage(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, FightsAddedToStagePayload::class.java)
        val stageId = payload?.stageId
        val fights = payload?.fights
        return if (!stageId.isNullOrBlank() && fights != null && stageDescriptorCrudRepository.existsById(stageId)) {
            jooqQueries.saveFights(fights.toList())
            listOf(event)
        } else {
            throw EventApplyingException("Stage ID is null or fights are null or could not find stage with id $stageId", event)
        }

    }

    private fun applyCategoryBracketsDroppedEvent(event: EventDTO): List<EventDTO> {
        jooqQueries.dropStages(event.categoryId)
        return listOf(event)
    }

    private fun applyCompetitorCategoryChangedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, ChangeCompetitorCategoryPayload::class.java)!!
        jooqQueries.changeCompetitorCategories(payload.fighterId, listOf(payload.oldCategoryId), listOf(payload.newCategoryId))
        return listOf(event)
    }

    private fun applyCompetitorUpdatedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, CompetitorUpdatedPayload::class.java)
        val competitor = payload?.fighter
        return if (competitor != null) {
            competitorCrudRepository.update(competitor.toCompetitor())
            listOf(event)
        } else {
            throw EventApplyingException("Competitor is null or such competitor does not exist.", event)
        }
    }


    private fun applyCompetitorAddedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, CompetitorAddedPayload::class.java)
        val competitor = payload?.fighter
        return if (competitor != null && !competitor.id.isNullOrBlank() && !competitor.categories.isNullOrEmpty()) {
            val comp = competitor.toCompetitor()
            log.info("Adding competitor: ${comp.id} to competition ${event.competitionId} and category ${competitor.categories}")
            competitorCrudRepository.insert(comp)
            jooqQueries.changeCompetitorCategories(competitor.id, emptyList(), competitor.categories.toList())
            listOf(event)
        } else {
            throw EventApplyingException("No competitor in the event payload: $event", event)
        }
    }

    private fun applyCategoryRegistrationStatusChanged(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, CategoryRegistrationStatusChangePayload::class.java)
        val newStatus = payload?.isNewStatus
        return if (newStatus != null) {
            val category = categoryDescriptorCrudRepository.findById(event.categoryId)
            category.registrationOpen = newStatus
            categoryDescriptorCrudRepository.update(category)
            listOf(event)
        } else {
            throw EventApplyingException("No competitor in the event payload: $event", event)
        }
    }


    private fun applyCompetitorRemovedEvent(event: EventDTO): List<EventDTO> {
        val competitorId = getPayloadAs(event.payload, CompetitorRemovedPayload::class.java)?.fighterId
        return if (competitorId != null) {
            competitorCrudRepository.deleteById(competitorId)
            listOf(event)
        } else {
            throw EventApplyingException("Competitor id is null.", event)
        }
    }

    private fun applyCompetitorMovedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, FightEditorChangesAppliedPayload::class.java)
        payload?.changes?.forEach { change ->
            change.changePatches.forEach {
                applyChange(it, change.selectedFightIds)
            }
        } ?: throw EventApplyingException("Payload is null, or changes are null.", event)
        return listOf(event)
    }

    private fun applyChange(jsonPatch: JsonPatch?, fightIds: Array<String>) {
        when (jsonPatch?.op) {
            "replace" -> {
                val path = jsonPatch.path
                val cs = jsonPatch.value
                val fightId = fightIds[jsonPatch.path[0].toInt()]
                if (path[1] == "scores") {
                    val index = min(path[2].toInt(), jooqQueries.getCompScoreSize(fightId))
                    jooqQueries.replaceFightScore(fightId, cs, index)
                } else {
                    log.warn("We only update scores.")
                }
            }
            else -> {
                log.warn("Unknown patch operation: ${jsonPatch?.op}")
            }
        }
    }

    private fun applyFighStartTimeUpdatedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, FightStartTimeUpdatedPayload::class.java)
        val newFights = payload?.newFights
        return if (!newFights.isNullOrEmpty()) {
            jdbcRepository.batchUpdateFightStartTimesMatPeriodNumber(newFights.filterNotNull())
            listOf(event)
        } else {
            throw EventApplyingException("Fights are null.", event)
        }
    }


    private fun applyBracketsGeneratedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, BracketsGeneratedPayload::class.java)
        val stages = payload?.stages
        val categoryId = event.categoryId
        return if (stages != null && !categoryId.isNullOrBlank()) {
            jooqQueries.saveStages(stages.mapIndexedNotNull { index, stage ->
                stage.setStageOrder(index) })
            jooqQueries.savePointsAssignments(stages.flatMap { it.pointsAssignments.toList() })
            jooqQueries.saveInputDescriptors(stages.mapNotNull { it.inputDescriptor })
            jooqQueries.saveResultDescriptors(stages.mapNotNull { it.stageResultDescriptor })
            listOf(event)
        } else {
            throw EventApplyingException("Fights are null or empty or category ID is empty.", event)
        }
    }

    private fun applyCategoryAddedEvent(event: EventDTO) {
        val c = getPayloadAs(event.payload, CategoryAddedPayload::class.java)?.categoryState
        if (c != null && event.categoryId != null && competitionPropertiesDao.existsById(event.competitionId) && c.category != null
                && !c.category.restrictions.isNullOrEmpty()) {
            log.info("Adding category: ${event.categoryId} to competition ${event.competitionId}")
            jooqQueries.saveCategoryDescriptor(c.category, event.competitionId)
        } else {
            throw EventApplyingException("event did not contain category state.", event)
        }
    }

    private fun applyCategoryStateDeletedEvent(event: EventDTO): List<EventDTO> {
        return if (!event.categoryId.isNullOrBlank()) {
            categoryDescriptorCrudRepository.deleteById(event.categoryId)
            listOf(event)
        } else {
            throw EventApplyingException("Category ID is null.", event)
        }
    }

    private val log = LoggerFactory.getLogger(CategoryEventProcessor::class.java)

    private fun <T> getPayloadAs(payload: String?, clazz: Class<T>): T? = mapper.getPayloadFromString(payload, clazz)
}