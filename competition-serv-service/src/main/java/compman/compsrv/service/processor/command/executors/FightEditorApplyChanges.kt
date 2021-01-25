package compman.compsrv.service.processor.command.executors

import arrow.core.curry
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.FightEditorApplyChangesPayload
import compman.compsrv.model.commands.payload.GroupChangeType
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.competition.CompScoreDTO
import compman.compsrv.model.dto.competition.FightDescriptionDTO
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.dto.competition.ScoreDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.processor.command.*
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.applyConditionalUpdate
import org.springframework.stereotype.Component

@Component
class FightEditorApplyChanges(mapper: ObjectMapper, validators: List<PayloadValidator>): ICommandExecutor<Category>, ValidatedExecutor<Category>(mapper, validators) {
    override fun execute(
        entity: Category,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Category> {
        return executeValidated<FightEditorApplyChangesPayload>(command) { payload, com ->
            entity to entity.process(payload, com, AbstractAggregateService.Companion::createEvent)
        }.unwrap(command)
    }

    fun Category.process(
        payload: FightEditorApplyChangesPayload,
        c: CommandDTO,
        createEvent: (command: CommandDTO, type: EventType, payload: Payload) -> EventDTO
    ): List<EventDTO> {
        val version = getVersion()
        val stage = stages.first { it.id == payload.stageId }
        val bracketsType = stage.bracketType
        val allStageFights = fights.filter { it.stageId == payload.stageId }
        val stageFights = when (bracketsType) {
            BracketType.GROUP -> {
                val fightsByGroupId = allStageFights.groupBy { it.groupId!! }
                val competitorChangesByGroupId = payload.competitorGroupChanges.groupBy { it.groupId }
                val groups = stage.groupDescriptors
                groups.flatMap { gr ->
                    val groupChanges = competitorChangesByGroupId[gr.id].orEmpty()
                    val groupFights = fightsByGroupId[gr.id].orEmpty()
                    groupChanges.sortedBy { CategoryAggregateService.changePriority[it.changeType] }
                        .fold(groupFights) { acc, ch ->
                            when (ch.changeType!!) {
                                GroupChangeType.ADD -> {
                                    createUpdatesWithAddedCompetitor(
                                        acc,
                                        ch,
                                        payload.stageId,
                                        c.competitionId,
                                        c.categoryId
                                    )
                                }
                                GroupChangeType.REMOVE -> {
                                    createUpdatesWithRemovedCompetitor(acc, ch, gr)
                                }
                            }
                        }
                }.sortedBy { it.numberInRound }
                    .mapIndexed { i, fightDescriptionDTO -> fightDescriptionDTO.setNumberInRound(i) }
            }
            else -> {
                allStageFights.map { f ->
                    payload.bracketsChanges.find { change -> change.fightId == f.id }?.let { change ->
                        if (change.competitors.isNullOrEmpty()) {
                            f.setScores(emptyArray())
                        } else {
                            val scores = f.scores.orEmpty()
                            f.setScores(change.competitors.mapIndexed { index, cmpId ->
                                scores.find { s -> s.competitorId == cmpId }
                                    ?: scores.find { s -> s.competitorId.isNullOrBlank() }?.setCompetitorId(cmpId)
                                    ?: CompScoreDTO()
                                        .setCompetitorId(cmpId)
                                        .setScore(ScoreDTO(0, 0, 0, emptyArray()))
                                        .setOrder(getMinUnusedOrder(scores, index))
                            }.toTypedArray())
                        }
                    } ?: f
                }
            }
        }

        val dirtyStageFights = stageFights.applyConditionalUpdate({ it.status == FightStatus.UNCOMPLETABLE },
            { it.setStatus(FightStatus.PENDING) })

        val clearedStageFights =
            clearAffectedFights(dirtyStageFights, payload.bracketsChanges.map { it.fightId }.toSet())

        val markedStageFights =
            FightsService.markAndProcessUncompletableFights(clearedStageFights, stage.stageStatus) { id ->
                (allStageFights.firstOrNull { it.id == id }
                    ?: stageFights.firstOrNull { it.id == id })?.scores?.toList()
            }

        return Category.createFightEditorChangesAppliedEvents(c,
            markedStageFights.filter { allStageFights.none { asf -> asf.id == it.id } },
            markedStageFights.filter { allStageFights.any { asf -> asf.id == it.id } },
            allStageFights.filter { asf -> markedStageFights.none { msf -> asf.id == msf.id } }
                .map { it.id }, createEvent
        ).map(this::enrichWithVersionAndNumber.curry()(version))
    }

    private final tailrec fun clearAffectedFights(
        fights: List<FightDescriptionDTO>,
        changedIds: Set<String>
    ): List<FightDescriptionDTO> {
        return if (changedIds.isEmpty()) {
            fights
        } else {
            val affectedFights =
                fights.filter { fg -> fg.scores?.any { s -> changedIds.contains(s.parentFightId) } == true }
            clearAffectedFights(fights.map { f ->
                f.setScores(
                    f.scores?.applyConditionalUpdate(
                        { changedIds.contains(it.parentFightId) },
                        { it.setCompetitorId(null) })
                )
            }, affectedFights.map { it.id }.toSet())
        }
    }

    private fun getMinUnusedOrder(scores: Array<out CompScoreDTO>?, index: Int = 0): Int {
        return if (scores.isNullOrEmpty()) {
            0
        } else {
            (0..scores.size + index).filter { i -> scores.none { s -> s.order == i } }[index]
        }
    }

    override val commandType: CommandType
        get() = CommandType.FIGHTS_EDITOR_APPLY_CHANGE


}