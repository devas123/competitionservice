package compman.compsrv.service.processor.command.executors

import arrow.core.curry
import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.aggregate.Category
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.commands.CommandType
import compman.compsrv.model.commands.payload.GenerateBracketsPayload
import compman.compsrv.model.commands.payload.Payload
import compman.compsrv.model.dto.brackets.FightResultOptionDTO
import compman.compsrv.model.dto.brackets.StageStatus
import compman.compsrv.model.dto.brackets.StageType
import compman.compsrv.model.dto.competition.CompetitorDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.BracketsGeneratedPayload
import compman.compsrv.model.events.payload.FightsAddedToStagePayload
import compman.compsrv.repository.DBOperations
import compman.compsrv.service.fight.FightServiceFactory
import compman.compsrv.service.processor.command.AbstractAggregateService
import compman.compsrv.service.processor.command.AggregateWithEvents
import compman.compsrv.service.processor.command.ICommandExecutor
import compman.compsrv.service.processor.command.ValidatedExecutor
import compman.compsrv.util.IDGenerator
import compman.compsrv.util.PayloadValidator
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class GenerateBrackets(
    mapper: ObjectMapper,
    validators: List<PayloadValidator>,
    private val fightsGenerateService: FightServiceFactory
) : ICommandExecutor<Category>, ValidatedExecutor<Category>(mapper, validators) {
    override fun execute(
        entity: Category,
        dbOperations: DBOperations,
        command: CommandDTO
    ): AggregateWithEvents<Category> =
        executeValidated<GenerateBracketsPayload>(command) { payload, com ->
            val competitors = dbOperations.getCategoryCompetitors(com.competitionId, com.categoryId, false)
            if (!competitors.isNullOrEmpty()) {
                entity to entity.process(
                    payload,
                    com,
                    fightsGenerateService,
                    competitors.map { it.competitorDTO },
                    AbstractAggregateService.Companion::createEvent
                )
            } else {
                throw IllegalArgumentException("No competitors or category not found.")
            }
        }.unwrap(command)


    fun Category.process(
        payload: GenerateBracketsPayload,
        c: CommandDTO,
        fightsGenerateService: FightServiceFactory,
        competitors: List<CompetitorDTO>,
        createEvent: (command: CommandDTO, type: EventType, payload: Payload) -> EventDTO
    ): List<EventDTO> {
        val version = getVersion()
        val stages = payload.stageDescriptors.sortedBy { it.stageOrder }
        val stageIdMap = stages
            .map { stage ->
                (stage.id
                    ?: error("Missing stage id")) to IDGenerator.stageId(c.competitionId, c.categoryId!!)
            }
            .toMap()
        val updatedStages = stages.map { stage ->
            val duration = stage.fightDuration ?: error("Missing fight duration.")
            val outputSize = when (stage.stageType) {
                StageType.PRELIMINARY -> stage.stageResultDescriptor.outputSize!!
                else -> 0
            }
            val stageId = stageIdMap[stage.id] ?: error("Generated stage id not found in the map.")
            val groupDescr = stage.groupDescriptors?.map { it ->
                it.setId(IDGenerator.groupId(stageId))
            }?.toTypedArray()
            val inputDescriptor = stage.inputDescriptor?.setId(stageId)?.setSelectors(
                stage.inputDescriptor
                    ?.selectors
                    ?.mapIndexed { index, sel ->
                        sel.setId("$stageId-s-$index")
                            .setApplyToStageId(stageIdMap[sel.applyToStageId])
                    }?.toTypedArray()
            )
            val enrichedOptions =
                stage.stageResultDescriptor?.fightResultOptions.orEmpty().toList() + FightResultOptionDTO.WALKOVER
            val resultDescriptor = stage.stageResultDescriptor
                .setId(stageId)
                .setFightResultOptions(enrichedOptions.map {
                    it
                        .setId(it.id ?: IDGenerator.hashString("$stageId-${IDGenerator.uid()}"))
                        .setLoserAdditionalPoints(it.loserAdditionalPoints ?: BigDecimal.ZERO)
                        .setLoserPoints(it.loserPoints ?: BigDecimal.ZERO)
                        .setWinnerAdditionalPoints(it.winnerAdditionalPoints ?: BigDecimal.ZERO)
                        .setWinnerPoints(it.winnerAdditionalPoints ?: BigDecimal.ZERO)
                }.distinctBy { it.id }.toTypedArray())
            val status =
                if (stage.stageOrder == 0) StageStatus.WAITING_FOR_APPROVAL else StageStatus.WAITING_FOR_COMPETITORS
            val stageWithIds = stage
                .setCategoryId(c.categoryId)
                .setId(stageId)
                .setStageStatus(status)
                .setCompetitionId(c.competitionId)
                .setGroupDescriptors(groupDescr)
                .setInputDescriptor(inputDescriptor)
                .setStageResultDescriptor(resultDescriptor)
            val size = if (stage.stageOrder == 0) competitors.size else stage.inputDescriptor.numberOfCompetitors!!
            val comps = if (stage.stageOrder == 0) {
                competitors
            } else {
                emptyList()
            }
            val twoFighterFights = fightsGenerateService.generateStageFights(
                c.competitionId, c.categoryId,
                stageWithIds,
                size, duration, comps, outputSize
            )
            stageWithIds
                .setName(stageWithIds.name ?: "Default brackets")
                .setStageStatus(status)
                .setInputDescriptor(inputDescriptor)
                .setStageResultDescriptor(stageWithIds.stageResultDescriptor)
                .setStageStatus(StageStatus.WAITING_FOR_COMPETITORS)
                .setNumberOfFights(twoFighterFights.size) to twoFighterFights
        }
        val fightAddedEvents = updatedStages.flatMap { pair ->
            pair.second.chunked(30).map {
                createEvent(
                    c, EventType.FIGHTS_ADDED_TO_STAGE,
                    FightsAddedToStagePayload(it.toTypedArray(), pair.first.id)
                )
            }
        }
        return listOf(
            createEvent(
                c,
                EventType.BRACKETS_GENERATED,
                BracketsGeneratedPayload(updatedStages.mapIndexedNotNull { index, pair ->  pair.first.setStageOrder(index) }.toTypedArray())
            )
        ) + fightAddedEvents.map(this::enrichWithVersionAndNumber.curry()(version))
    }

    override val commandType: CommandType
        get() = CommandType.GENERATE_BRACKETS_COMMAND


}