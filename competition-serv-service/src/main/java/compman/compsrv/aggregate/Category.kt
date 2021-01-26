package compman.compsrv.aggregate

import arrow.core.Either
import arrow.core.right
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.FightEditorChangesAppliedPayload
import compman.compsrv.service.processor.CreateEvent
import compman.compsrv.service.processor.LabeledFight
import java.util.concurrent.atomic.AtomicLong


data class Category(
    val id: String,
    val descriptor: CategoryDescriptorDTO,
    val fights: Array<FightDescriptionDTO> = emptyArray(),
    val stages: Array<StageDescriptorDTO> = emptyArray(),
    val competitors: Array<String> = emptyArray(),
    val numberOfCompetitors: Int = 0
) : AbstractAggregate(AtomicLong(0), AtomicLong(0)) {
    companion object {
        fun createFightEditorChangesAppliedEvents(
            command: CommandDTO,
            newFights: List<FightDescriptionDTO>,
            updates: List<FightDescriptionDTO>,
            removeFightIds: List<String>,
            createEvent: CreateEvent
        ): List<EventDTO> {
            val allFights = newFights.map { LabeledFight(it, LabeledFight.NEW) } + updates.map {
                LabeledFight(
                    it,
                    LabeledFight.UPDATED
                )
            } + removeFightIds.map { LabeledFight(Either.left(Unit), LabeledFight.REMOVED, it.right()) }
            return allFights.chunked(50) { chunk ->
                createEvent(command, EventType.FIGHTS_EDITOR_CHANGE_APPLIED, FightEditorChangesAppliedPayload()
                    .setNewFights(chunk.filter { it.label == LabeledFight.NEW }.mapNotNull { it.fight.orNull() }
                        .toTypedArray())
                    .setUpdates(chunk.filter { it.label == LabeledFight.UPDATED }.mapNotNull { it.fight.orNull() }
                        .toTypedArray())
                    .setRemovedFighids(chunk.filter { it.label == LabeledFight.REMOVED }.mapNotNull { it.id.orNull() }
                        .toTypedArray())
                )
            }
        }
    }

    val fightsMap = fights.map { it.id to it }.toMap()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Category

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}