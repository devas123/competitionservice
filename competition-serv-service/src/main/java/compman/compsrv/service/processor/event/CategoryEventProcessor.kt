package compman.compsrv.service.processor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.CategoryDescriptor
import compman.compsrv.jpa.competition.CompetitionState
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.commands.payload.JsonPatch
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.math.min


@Component
class CategoryEventProcessor(private val mapper: ObjectMapper) : IEventProcessor<CompetitionState> {
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

    override fun applyEvent(state: CompetitionState, event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
        return when (event.type) {
            EventType.COMPETITOR_ADDED -> applyCompetitorAddedEvent(state, event)
            EventType.COMPETITOR_REMOVED -> applyCompetitorRemovedEvent(state, event)
            EventType.COMPETITOR_UPDATED, EventType.COMPETITOR_CATEGORY_CHANGED -> applyCompetitorUpdatedEvent(state, event)
            EventType.FIGHTS_EDITOR_CHANGE_APPLIED -> applyCompetitorMovedEvent(state, event)
            EventType.BRACKETS_GENERATED -> applyBracketsGeneratedEvent(state, event)
            EventType.FIGHTS_START_TIME_UPDATED -> applyFighStartTimeUpdatedEvent(state, event)
            EventType.CATEGORY_DELETED -> applyCategoryStateDeletedEvent(state, event)
            EventType.CATEGORY_BRACKETS_DROPPED -> applyCategoryBracketsDroppedEvent(state, event)
            EventType.CATEGORY_ADDED -> applyCategoryAddedEvent(state, event)
            EventType.DUMMY -> state to listOf(event)
            else -> {
                log.warn("Unknown event type: ${event.type}")
                state to emptyList()
            }
        }
    }

    private fun applyCategoryBracketsDroppedEvent(state: CompetitionState, event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
        state.categories.first { it.id == event.categoryId }.brackets?.fights?.clear()
        return state to listOf(event)
    }

    private fun applyCompetitorUpdatedEvent(state: CompetitionState, event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
        val payload = getPayloadAs(event.payload, CompetitorUpdatedPayload::class.java)
        val competitor = payload?.fighter
        return if (competitor != null) {
            val comp = competitor.toEntity { id -> state.categories.first { it.id == id }.category }
            comp.categories?.forEach {cat ->
                state.categories.first { it.id == cat.id }.category?.competitors?.add(comp)
            }
            state to listOf(event)
        } else {
            throw EventApplyingException("Competitor is null or such competitor does not exist.", event)
        }
    }


    private fun applyCompetitorAddedEvent(state: CompetitionState, event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
        val payload = getPayloadAs(event.payload, CompetitorAddedPayload::class.java)
        val competitor = payload?.fighter
        return if (competitor != null && !competitor.id.isNullOrBlank()) {
            val comp = competitor.toEntity { id -> state.categories.first { it.id == id }.category }
            log.info("Adding competitor: ${comp.id} to competition ${event.competitionId} and category ${competitor.categories}")
            comp.categories?.map {cat ->
                state.categories.first { it.id == cat.id }.category?.competitors?.add(comp)
            }
            state to listOf(event)
        } else {
            throw EventApplyingException("No competitor in the event payload: $event", event)
        }
    }


    private fun applyCompetitorRemovedEvent(state: CompetitionState, event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
        val competitorId = getPayloadAs(event.payload, CompetitorRemovedPayload::class.java)?.fighterId
        return if (competitorId != null) {
            state.categories.forEach { it.category?.competitors?.removeIf { com -> com.id == competitorId } }
            state to listOf(event)
        } else {
            throw EventApplyingException("Competitor id is null.", event)
        }
    }

    private fun applyCompetitorMovedEvent(state: CompetitionState, event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
        val payload = getPayloadAs(event.payload, FightEditorChangesAppliedPayload::class.java)
        val category = state.categories.first { it.id == event.categoryId }
        payload?.changes?.forEach { change ->
            change.changePatches.forEach {
                applyChange(category.category!!, category.brackets?.fights!!, it, change.selectedFightIds)
            }
        } ?: throw EventApplyingException("Payload is null, or changes are null.", event)
        return state to listOf(event)
    }

    private fun applyChange(category: CategoryDescriptor, fights: List<FightDescription>, jsonPatch: JsonPatch?, fightIds: Array<String>) {
        when (jsonPatch?.op) {
            "replace" -> {
                val path = jsonPatch.path
                val fight = fights.first { it.id == fightIds[jsonPatch.path[0].toInt()] }
                if (path[1] == "scores") {
                    val index = min(path[2].toInt(), fight.scores?.size ?: 0)
                    if (fight.scores == null) {
                        fight.scores = mutableListOf()
                    }
                    fight.scores?.set(index, jsonPatch.value.toEntity { category })
                } else {
                    log.warn("We only update scores.")
                }
            }
            else -> {
                log.warn("Unknown patch operation: ${jsonPatch?.op}")
            }
        }
    }

    private fun applyFighStartTimeUpdatedEvent(state: CompetitionState, event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
        val payload = getPayloadAs(event.payload, FightStartTimeUpdatedPayload::class.java)
        val newFights = payload?.newFights
        val allFightsExist = newFights?.let { array ->
            val allFights = state.categories.mapNotNull { it.brackets }.flatMap { it.fights ?: mutableListOf() }.mapNotNull { it.id }
            array.all { allFights.contains(it.fightId) }
        }
        return if (newFights != null && allFightsExist == true) {
            newFights.forEach { fightCrudRepository.updateStartTimeAndMatById(it.fightId, it.startTime, it.matId) }
            state to listOf(event)
        } else {
            throw EventApplyingException("Fights are null or not all fights are present in the repository.", event)
        }
    }

    private fun applyBracketsGeneratedEvent(event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
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

    private fun applyCategoryAddedEvent(event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
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

    private fun applyCategoryStateDeletedEvent(event: EventDTO): Pair<CompetitionState, List<EventDTO>> {
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