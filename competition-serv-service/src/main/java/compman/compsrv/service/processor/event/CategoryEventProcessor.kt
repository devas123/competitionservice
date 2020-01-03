package compman.compsrv.service.processor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.commands.payload.JsonPatch
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.*
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import kotlin.math.min


@Component
class CategoryEventProcessor(private val mapper: ObjectMapper,
                             private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                             private val categoryCrudRepository: CategoryStateCrudRepository,
                             private val categoryDescriptorCrudRepository: CategoryDescriptorCrudRepository,
                             private val competitorCrudRepository: CompetitorCrudRepository,
                             private val fightCrudRepository: FightCrudRepository,
                             private val bracketsCrudRepository: BracketsCrudRepository) : IEventProcessor {
    override fun affectedEvents(): Set<EventType> {
        return setOf(
                EventType.COMPETITOR_ADDED,
                EventType.COMPETITOR_REMOVED,
                EventType.COMPETITOR_UPDATED,
                EventType.COMPETITOR_UPDATED,
                EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
                EventType.BRACKETS_GENERATED,
                EventType.FIGHTS_START_TIME_UPDATED,
                EventType.CATEGORY_DELETED,
                EventType.CATEGORY_BRACKETS_DROPPED,
                EventType.CATEGORY_ADDED,
                EventType.COMPETITOR_CATEGORY_CHANGED)
    }

    override fun applyEvent(event: EventDTO): List<EventDTO> {
        return when (event.type) {
            EventType.COMPETITOR_ADDED -> applyCompetitorAddedEvent(event)
            EventType.COMPETITOR_REMOVED -> applyCompetitorRemovedEvent(event)
            EventType.COMPETITOR_UPDATED, EventType.COMPETITOR_CATEGORY_CHANGED -> applyCompetitorUpdatedEvent(event)
            EventType.FIGHTS_EDITOR_CHANGE_APPLIED -> applyCompetitorMovedEvent(event)
            EventType.BRACKETS_GENERATED -> applyBracketsGeneratedEvent(event)
            EventType.FIGHTS_START_TIME_UPDATED -> applyFighStartTimeUpdatedEvent(event)
            EventType.CATEGORY_DELETED -> applyCategoryStateDeletedEvent(event)
            EventType.CATEGORY_BRACKETS_DROPPED -> applyCategoryBracketsDroppedEvent(event)
            EventType.CATEGORY_ADDED -> applyCategoryAddedEvent(event)
            EventType.DUMMY -> listOf(event)
            else -> {
                log.warn("Unknown event type: ${event.type}")
                emptyList()
            }
        }
    }

    private fun applyCategoryBracketsDroppedEvent(event: EventDTO): List<EventDTO> {
        bracketsCrudRepository.deleteById(event.categoryId!!)
        return listOf(event)
    }

    private fun applyCompetitorUpdatedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, CompetitorUpdatedPayload::class.java)
        val competitor = payload?.fighter
        return if (competitor != null) {
            competitorCrudRepository.save(competitor.toEntity { categoryDescriptorCrudRepository.findByIdOrNull(it) })
            listOf(event)
        } else {
            throw EventApplyingException("Competitor is null or such competitor does not exist.", event)
        }
    }


    private fun applyCompetitorAddedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, CompetitorAddedPayload::class.java)
        val competitor = payload?.fighter
        return if (competitor != null && !competitor.id.isNullOrBlank()) {
            val comp = competitor.toEntity { categoryDescriptorCrudRepository.findByIdOrNull(it) }
            log.info("Adding competitor: ${comp.id} to competition ${event.competitionId} and category ${comp.categories?.map { it.id }}")
            competitorCrudRepository.save(comp)
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
                val fight = fightCrudRepository.getOne(fightIds[jsonPatch.path[0].toInt()])
                if (path[1] == "scores") {
                    val index = min(path[2].toInt(), fight.scores?.size ?: 0)
                    if (fight.scores == null) {
                        fight.scores = mutableListOf()
                    }
                    fight.scores?.set(index, jsonPatch.value.toEntity { categoryDescriptorCrudRepository.findByIdOrNull(it) })
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
        val allFightsExist = newFights?.let { array ->
            fightCrudRepository.findAllById(array.map { it.fightId }).size == array.size
        }
        return if (newFights != null && allFightsExist == true) {
            newFights.forEach { fightCrudRepository.updateStartTimeAndMatById(it.fightId, it.startTime, it.matId) }
            listOf(event)
        } else {
            throw EventApplyingException("Fights are null or not all fights are present in the repository.", event)
        }
    }

    private fun applyBracketsGeneratedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, BracketsGeneratedPayload::class.java)
        val fights = payload?.fights
        val bracketType = payload?.bracketType
        val categoryId = event.categoryId
        val compId = event.competitionId
        return if (fights != null && !categoryId.isNullOrBlank()) {
            val categories = categoryDescriptorCrudRepository.findAllById(fights.map { it.categoryId }.toSet()).groupBy { it.id }
            bracketsCrudRepository.save(BracketDescriptor(categoryId, compId, bracketType
                    ?: BracketType.SINGLE_ELIMINATION, fights.mapNotNull { it.toEntity { id -> categories[id]?.firstOrNull() } }.toMutableList()))
            listOf(event)
        } else {
            throw EventApplyingException("Fights are null or empty or category ID is empty.", event)
        }
    }

    private fun applyCategoryAddedEvent(event: EventDTO): List<EventDTO> {
        val c = getPayloadAs(event.payload, CategoryAddedPayload::class.java)?.categoryState
        val competitionState = competitionStateCrudRepository.findByIdOrNull(event.competitionId)
        return if (c != null && event.categoryId != null && competitionState != null) {
            log.info("Adding category: ${event.categoryId} to competition ${event.competitionId}")
            val newState = c.toEntity(competitionState) { competitorCrudRepository.findByIdOrNull(it) }
            newState.category?.let {
                categoryDescriptorCrudRepository.saveAndFlush(it)
                categoryCrudRepository.save(newState)
                listOf(event)
            } ?: emptyList()
        } else {
            throw EventApplyingException("event did not contain category state.", event)
        }
    }

    private fun applyCategoryStateDeletedEvent(event: EventDTO): List<EventDTO> {
        return if (!event.categoryId.isNullOrBlank()) {
            categoryCrudRepository.deleteById(event.categoryId)
            listOf(event)
        } else {
            throw EventApplyingException("Category ID is null.", event)
        }
    }
    private val log = LoggerFactory.getLogger(CategoryEventProcessor::class.java)

    private fun <T> getPayloadAs(payload: String?, clazz: Class<T>): T? {
        if (payload != null) {
            return mapper.readValue(payload, clazz)
        }
        return null
    }
}