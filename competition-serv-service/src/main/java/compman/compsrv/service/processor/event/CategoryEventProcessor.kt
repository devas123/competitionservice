package compman.compsrv.service.processor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.CategoryState
import compman.compsrv.jpa.competition.Competitor
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


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
                EventType.FIGHTS_EDITOR_CHANGE_APPLIED,
                EventType.BRACKETS_GENERATED,
                EventType.FIGHTS_START_TIME_UPDATED,
                EventType.CATEGORY_DELETED,
                EventType.CATEGORY_BRACKETS_DROPPED,
                EventType.CATEGORY_ADDED)
    }

    override fun applyEvent(event: EventDTO): List<EventDTO> {
        return when (event.type) {
            EventType.COMPETITOR_ADDED -> applyCompetitorAddedEvent(event)
            EventType.COMPETITOR_REMOVED -> applyCompetitorRemovedEvent(event)
            EventType.COMPETITOR_UPDATED -> applyCompetitorUpdatedEvent(event)
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
        return if (competitor != null && competitorCrudRepository.existsById(competitor.id)) {
            competitorCrudRepository.save(Competitor.fromDTO(competitor))
            listOf(event)
        } else {
            throw EventApplyingException("Competitor is null or such competitor does not exist.", event)
        }
    }


    private fun applyCompetitorAddedEvent(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, CompetitorAddedPayload::class.java)
        val competitor = payload?.fighter
        return if (competitor != null && !competitor.id.isNullOrBlank() && !competitorCrudRepository.existsById(competitor.id)) {
            competitorCrudRepository.save(Competitor.fromDTO(competitor))
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
        val updatedSourceFight = payload?.updatedSourceFight
        val updatedTargetFight = payload?.updatedTargetFight
        return if (updatedSourceFight != null && updatedTargetFight != null && fightCrudRepository.existsById(updatedSourceFight.id) && fightCrudRepository.existsById(updatedTargetFight.id)) {
            fightCrudRepository.saveAll(listOf(FightDescription.fromDTO(updatedSourceFight), FightDescription.fromDTO(updatedTargetFight)))
            listOf(event)
        } else {
            throw EventApplyingException("Source fight or target fight is null or does not exist.", event)
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
            bracketsCrudRepository.save(BracketDescriptor(categoryId, compId, bracketType
                    ?: BracketType.SINGLE_ELIMINATION, fights.map { FightDescription.fromDTO(it) }.toTypedArray()))
            listOf(event)
        } else {
            throw EventApplyingException("Fights are null or empty or category ID is empty.", event)
        }
    }

    private fun applyCategoryAddedEvent(event: EventDTO): List<EventDTO> {
        val c = getPayloadAs(event.payload, CategoryAddedPayload::class.java)?.categoryState
        val competitionState = competitionStateCrudRepository.findById(event.competitionId)
        return if (c != null && event.categoryId != null && competitionState.isPresent) {
            val newState = CategoryState.fromDTO(c, competitionState.get(), emptySet())
            newState.category?.let {
                categoryDescriptorCrudRepository.saveAndFlush(it)
            }
            val categoryState = categoryCrudRepository.save(CategoryState.fromDTO(c, competitionState.get(), emptySet()))
            listOf(event.setPayload(writePayloadAsString(CategoryAddedPayload(categoryState.toDTO(includeCompetitors = true, includeBrackets = true)))))
        } else {
            throw EventApplyingException("event did not contain category state.", event)
        }
    }

    private fun applyCategoryStateDeletedEvent(event: EventDTO): List<EventDTO> {
        return if (!event.categoryId.isNullOrBlank()) {
            if (categoryCrudRepository.existsById(event.categoryId)) {
                categoryCrudRepository.deleteById(event.categoryId)
            }
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

    private fun writePayloadAsString(payload: Any): String = mapper.writeValueAsString(payload)
}