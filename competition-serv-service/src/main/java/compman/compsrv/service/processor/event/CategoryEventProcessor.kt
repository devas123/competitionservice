package compman.compsrv.service.processor.event

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.jpa.competition.FightDescription
import compman.compsrv.mapping.toEntity
import compman.compsrv.model.commands.payload.CategoryRegistrationStatusChangePayload
import compman.compsrv.model.commands.payload.JsonPatch
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.*
import compman.compsrv.model.exceptions.EventApplyingException
import compman.compsrv.repository.*
import compman.compsrv.util.getPayloadAs
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.util.*
import javax.persistence.EntityManager
import kotlin.math.min


@Component
class CategoryEventProcessor(private val mapper: ObjectMapper,
                             private val competitionStateCrudRepository: CompetitionStateCrudRepository,
                             private val categoryCrudRepository: CategoryStateCrudRepository,
                             private val categoryDescriptorCrudRepository: CategoryDescriptorCrudRepository,
                             private val categoryRestrictionCrudRepository: CategoryRestrictionCrudRepository,
                             private val competitorCrudRepository: CompetitorCrudRepository,
                             private val jdbcTemplate: JdbcTemplate,
                             private val fightCrudRepository: FightCrudRepository,
                             private val stageDescriptorCrudRepository: StageDescriptorCrudRepository,
                             private val bracketsDescriptorCrudRepository: BracketsDescriptorCrudRepository) : IEventProcessor {
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
        return when (event.type) {
            EventType.CATEGORY_REGISTRATION_STATUS_CHANGED -> applyCategoryRegistrationStatusChanged(event)
            EventType.COMPETITOR_ADDED -> applyCompetitorAddedEvent(event)
            EventType.COMPETITOR_REMOVED -> applyCompetitorRemovedEvent(event)
            EventType.COMPETITOR_UPDATED, EventType.COMPETITOR_CATEGORY_CHANGED -> applyCompetitorUpdatedEvent(event)
            EventType.FIGHTS_EDITOR_CHANGE_APPLIED -> applyCompetitorMovedEvent(event)
            EventType.BRACKETS_GENERATED -> applyBracketsGeneratedEvent(event)
            EventType.FIGHTS_ADDED_TO_STAGE -> applyFightsAddedToStage(event)
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

    private val findStageFights = { st: String?->
        st?.let {
            fightCrudRepository.findAllByStageId(it).collect({ mutableListOf() }, { list: MutableList<FightDescription>?, fight: FightDescription ->
                list?.add(fight)
            }, { t, u -> t.addAll(u) })
        }
    }


    private fun applyFightsAddedToStage(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, FightsAddedToStagePayload::class.java)
        val stageId = payload?.stageId
        val fights = payload?.fights
        return if (!stageId.isNullOrBlank() && fights != null) {
            stageDescriptorCrudRepository.findByIdOrNull(stageId)?.let {
                it.fights?.addAll(fights.map { fight -> fight.toEntity { categoryDescriptorCrudRepository.getOne(it) }})
                listOf(event)
            } ?: throw EventApplyingException("Could not find stage with id $stageId", event)
        } else {
            throw EventApplyingException("Stage ID is null or fights are null", event)
        }

    }

    private fun applyCategoryBracketsDroppedEvent(event: EventDTO): List<EventDTO> {
        categoryCrudRepository.getOne(event.categoryId!!).brackets?.stages?.clear()
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

    private fun applyCategoryRegistrationStatusChanged(event: EventDTO): List<EventDTO> {
        val payload = getPayloadAs(event.payload, CategoryRegistrationStatusChangePayload::class.java)
        val newStatus = payload?.isNewStatus
        return if (newStatus != null) {
            val category = categoryDescriptorCrudRepository.getOne(event.categoryId)
            category.registrationOpen = newStatus
            listOf(event)
        } else {
            throw EventApplyingException("No competitor in the event payload: $event", event)
        }
    }


    private fun applyCompetitorRemovedEvent(event: EventDTO): List<EventDTO> {
        val competitorId = getPayloadAs(event.payload, CompetitorRemovedPayload::class.java)?.fighterId
        return if (competitorId != null) {
            categoryCrudRepository.getOne(event.categoryId).category?.competitors?.removeIf { it.id == competitorId }
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
        return if (!newFights.isNullOrEmpty()) {
            jdbcTemplate.batchUpdate("UPDATE fight_description f SET start_time = ?, mat_id = ?, number_on_mat = ?, period = ? WHERE f.id = ?", object: BatchPreparedStatementSetter {
                override fun setValues(ps: PreparedStatement, i: Int) {
                    if (i < newFights.size) {
                        val fight = newFights[i]
                        fight?.let {
                            ps.setTimestamp(1, Timestamp.from(it.startTime))
                            ps.setString(2, it.matId)
                            ps.setInt( 3, it.fightNumber)
                            ps.setString(4, it.periodId)
                            ps.setString(5, it.fightId)
                        }
                    }
                }
                override fun getBatchSize(): Int = newFights.size

            })
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
            val catState = categoryCrudRepository.getOne(categoryId)
            catState.brackets?.stages?.clear()
            catState.brackets?.stages!!.addAll(stages.mapNotNull { stage ->
                stage.toEntity({ id -> competitorCrudRepository.findByIdOrNull(id) }, findStageFights)
            })
            listOf(event)
        } else {
            throw EventApplyingException("Fights are null or empty or category ID is empty.", event)
        }
    }

    private fun applyCategoryAddedEvent(event: EventDTO): List<EventDTO> {
        val c = getPayloadAs(event.payload, CategoryAddedPayload::class.java)?.categoryState
        val competitionState = competitionStateCrudRepository.findByIdOrNull(event.competitionId)
        return if (c != null && event.categoryId != null && competitionState != null && c.category != null
                && !c.category.restrictions.isNullOrEmpty()) {
            log.info("Adding category: ${event.categoryId} to competition ${event.competitionId}")
            val newState = c.toEntity(competitionState, { competitorCrudRepository.findByIdOrNull(it) }, findStageFights)
            newState.category?.let {
                val restrictions = it.restrictions?.map { res -> categoryRestrictionCrudRepository.save(res) }
                val brackets = bracketsDescriptorCrudRepository.save(newState.brackets!!)
                it.restrictions = restrictions?.toMutableSet()
                newState.category = categoryDescriptorCrudRepository.save(it)
                newState.brackets = brackets
                competitionState.categories.add(newState)
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

    private fun <T> getPayloadAs(payload: String?, clazz: Class<T>): T? = mapper.getPayloadAs(payload, clazz)
}