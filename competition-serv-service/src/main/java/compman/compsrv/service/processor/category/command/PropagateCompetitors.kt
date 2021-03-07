package compman.compsrv.service.processor.category.command

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.config.CATEGORY_COMMAND_EXECUTORS
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.PropagateCompetitorsPayload
import compman.compsrv.model.dto.brackets.StageDescriptorDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.CompetitorAssignmentDescriptor
import compman.compsrv.model.events.payload.CompetitorsPropagatedToStagePayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.service.processor.*
import compman.compsrv.util.Constants
import compman.compsrv.util.PayloadValidator
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

@Component
@Qualifier(CATEGORY_COMMAND_EXECUTORS)
class PropagateCompetitors(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>,
    private val fightsGenerateService: FightServiceFactory
) : ICommandExecutor<Category>, ValidatedCommandExecutor<Category>(mapper, validators) {
    override fun execute(
        entity: Category?,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Category> =
        entity?.let {
            executeValidated<PropagateCompetitorsPayload>(command) { p, com ->
                val competitors = dbOperations.getCategoryCompetitors(command.categoryId, false)
                entity to entity.process(
                    p,
                    com,
                    dbOperations,
                    competitors.map { it.competitorDTO },
                    AbstractAggregateService.Companion::createEvent
                )
            }.unwrap(command)
        } ?: error(Constants.CATEGORY_NOT_FOUND)

    private fun Category.process(
        p: PropagateCompetitorsPayload,
        c: CommandDTO,
        dbOperations: DBOperations,
        competitors: List<CompetitorDTO>,
        createEvent: CreateEvent
    ): List<EventDTO> {
        val stage = stages.getValue(p.previousStageId)
        val propagatedCompetitors = findPropagatedCompetitors(stage.dto, dbOperations, p).toSet()
        val propagatedStageFights = dbOperations.getFights(stages.getValue(p.propagateToStageId).fights.toList())

        val competitorIdsToFightIds = fightsGenerateService
            .distributeCompetitors(
                competitors.filter { propagatedCompetitors.contains(it.id) },
                propagatedStageFights,
                stage.dto.bracketType
            )
            .fold(emptyList<CompetitorAssignmentDescriptor>()) { acc, f ->
                val newPairs = f.scores?.mapNotNull {
                    it.competitorId?.let { c ->
                        CompetitorAssignmentDescriptor().setCompetitorId(c)
                            .setToFightId(f.id)
                    }
                }.orEmpty()
                acc + newPairs
            }
        return listOf(
            createEvent(
                c, EventType.COMPETITORS_PROPAGATED_TO_STAGE, CompetitorsPropagatedToStagePayload()
                    .setStageId(p.propagateToStageId)
                    .setPropagations(competitorIdsToFightIds)
            )
        )
    }

    private fun Category.findPropagatedCompetitors(
        stage: StageDescriptorDTO,
        dbOperations: DBOperations,
        p: PropagateCompetitorsPayload): List<String> {
        return fightsGenerateService.applyStageInputDescriptorToResultsAndFights(stage.bracketType,
            stage.inputDescriptor,
            p.previousStageId,
            { id -> stages.getValue(id).dto.stageResultDescriptor.fightResultOptions.orEmpty().toList() },
            { id -> stages.getValue(id).dto.stageResultDescriptor.competitorResults.orEmpty().toList() },
            { id -> dbOperations.getFights(stages.getValue(id).fights.toList()) })
    }



    override val commandType: CommandType
        get() = CommandType.PROPAGATE_COMPETITORS_COMMAND
}