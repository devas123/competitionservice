package compman.compsrv.service.processor.category.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.SetFightResultPayload
import compman.compsrv.model.dto.brackets.FightReferenceType
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.competition.FightStatus
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorAssignmentDescriptor
import compman.compsrv.model.events.payload.FightCompetitorsAssignedPayload
import compman.compsrv.model.events.payload.StageResultSetPayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.service.fight.FightsService
import compman.compsrv.service.processor.*
import compman.compsrv.util.Constants.CATEGORY_NOT_FOUND
import compman.compsrv.util.PayloadValidator
import compman.compsrv.util.copy
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_COMMAND_EXECUTORS)
class SetFightResult(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>,
    private val fightsGenerateService: FightServiceFactory) : ICommandExecutor<Category>, ValidatedCommandExecutor<Category>(mapper, validators) {
    override fun execute(
        entity: Category?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Category> =
        entity?.let {
            executeValidated<SetFightResultPayload>(command) { payload, com ->
                entity to entity.process(
                    payload,
                    com,
                    fightsGenerateService,
                    AbstractAggregateService.Companion::createEvent
                )
            }.unwrap(command)
        } ?: error(CATEGORY_NOT_FOUND)


    private fun Category.process(
        payload: SetFightResultPayload,
        c: CommandDTO,
        fightsGenerateService: FightServiceFactory,
        createEvent: CreateEvent
    ): List<EventDTO> {
        val stageId = fights.find { it.id == payload.fightId }?.stageId
            ?: error("Did not find stage id for fight ${payload.fightId}")
        val result = emptyList<EventDTO>()
        val finishedFights = mutableSetOf<String>()
        val stageFights = fights.filter { it.stageId == stageId }
        val fight = stageFights.find { f -> f.id == payload.fightId }
            ?: error("No fight with id ${payload.fightId} found")
        val winnerId = payload.fightResult?.winnerId

        fun getIdToProceed(ref: FightReferenceType): String? {
            return when (ref) {
                FightReferenceType.WINNER ->
                    winnerId
                FightReferenceType.LOSER, FightReferenceType.PROPAGATED ->
                    payload.scores?.find { s -> s.competitorId != winnerId }?.competitorId
            }
        }

        val fightUpdates = result +
                if (!winnerId.isNullOrBlank()) {
                    val assignments = mutableListOf<EventDTO>()
                    FightReferenceType.values().forEach { ref ->
                        getIdToProceed(ref)?.let {
                            FightsService.moveFighterToSiblings(
                                it,
                                payload.fightId,
                                ref,
                                stageFights
                            ) { fromFightId, toFightId, competitorId ->
                                assignments.add(
                                    createEvent(
                                        c,
                                        EventType.DASHBOARD_FIGHT_COMPETITORS_ASSIGNED,
                                        FightCompetitorsAssignedPayload()
                                            .setAssignments(
                                                arrayOf(
                                                    CompetitorAssignmentDescriptor().setFromFightId(fromFightId)
                                                        .setToFightId(toFightId).setCompetitorId(competitorId)
                                                        .setReferenceType(ref)
                                                )
                                            )
                                    )
                                )
                            }
                        }
                    }
                    finishedFights.add(payload.fightId)
                    listOf(createEvent(c, EventType.DASHBOARD_FIGHT_RESULT_SET, payload)) + assignments
                } else {
                    emptyList()
                }

        return fightUpdates + if (checkIfAllStageFightsFinished(fight.stageId, finishedFights)) {
            val stage = stages.first { it.id == stageId }
            val fightsWithResult = stageFights.map { fd ->
                if (fd.id == payload.fightId) {
                    fd.copy(fightResult = payload.fightResult)
                } else {
                    fd
                }
            }
            val fightResultOptions = stage.stageResultDescriptor?.fightResultOptions.orEmpty().toList()
            val stageResults = fightsGenerateService.buildStageResults(
                stage.bracketType, StageStatus.FINISHED, stage.stageType,
                fightsWithResult, stage.id!!, stage.competitionId, fightResultOptions
            )
            listOf(
                createEvent(
                    c, EventType.DASHBOARD_STAGE_RESULT_SET,
                    StageResultSetPayload()
                        .setStageId(stage.id)
                        .setResults(stageResults.toTypedArray())
                )
            )
        } else {
            emptyList()
        }
    }

    private fun Category.checkIfAllStageFightsFinished(stageId: String?, additionalFinishedFightIds: Set<String>) =
        stageId?.let { sid ->
            fights.filter { it.stageId == sid }
                .all {
                    it.status == FightStatus.FINISHED || it.status == FightStatus.WALKOVER || it.status == FightStatus.UNCOMPLETABLE || additionalFinishedFightIds.contains(
                        it.id
                    )
                }
        }
            ?: false



    override val commandType: CommandType
        get() = CommandType.DASHBOARD_SET_FIGHT_RESULT_COMMAND


}