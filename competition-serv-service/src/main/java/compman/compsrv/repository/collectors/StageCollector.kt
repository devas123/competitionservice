package compman.compsrv.repository.collectors

import com.compmanager.compservice.jooq.tables.*
import compman.compsrv.model.dto.brackets.*
import org.jooq.Record
import java.lang.IllegalStateException
import java.util.*
import java.util.function.BiConsumer
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collector

class StageDescriptorAccumulator(val stageDescriptorDTO: StageDescriptorDTO) {
    val fightResultOptions = TreeSet(Comparator.comparing { fr: FightResultOptionDTO -> fr.id!! })
    val groups = TreeSet(Comparator.comparing { gr: GroupDescriptorDTO -> gr.id!! })
    val additionalGroupSorting = TreeSet(Comparator.comparing { agsd: AdditionalGroupSortingDescriptorDTO -> agsd.groupSortSpecifier.name })
    val stageResults = StageResultAccumulator(StageResultDescriptorDTO().setId(stageDescriptorDTO.id))
    val stageInputs = StageInputAccumulator(StageInputDescriptorDTO().setId(stageDescriptorDTO.id))
}

class StageResultAccumulator(val stageResultDescriptor: StageResultDescriptorDTO) {
    val competitorResults = TreeSet(Comparator.comparing { t: CompetitorStageResultDTO -> t.competitorId!! + t.stageId!! })
}

class StageInputAccumulator(val inputDescriptor: StageInputDescriptorDTO) {
    val competitorSelectorAccumulators = TreeSet(Comparator.comparing { t: CompetitorSelectorAccumulator -> t.competitorSelector.id!! })
}

class CompetitorSelectorAccumulator(val competitorSelector: CompetitorSelectorDTO) {
    val selectorValues = mutableSetOf<String>()
}

class StageCollector : Collector<Record, StageDescriptorAccumulator, StageDescriptorDTO> {
    private fun createStageDescriptorFromAccumulator(accumulator: StageDescriptorAccumulator): StageDescriptorDTO {
        return accumulator.stageDescriptorDTO
                .setGroupDescriptors(accumulator.groups.toTypedArray())
                .setStageResultDescriptor(accumulator.stageResults.stageResultDescriptor
                        .setFightResultOptions(accumulator.fightResultOptions.toTypedArray())
                        .setCompetitorResults(accumulator.stageResults.competitorResults.toTypedArray())
                        .setAdditionalGroupSortingDescriptors(accumulator.additionalGroupSorting.toTypedArray()))
                .setInputDescriptor(accumulator.stageInputs.inputDescriptor
                        .setSelectors(accumulator.stageInputs.competitorSelectorAccumulators
                                .map { db -> db.competitorSelector.setSelectorValue(db.selectorValues.toTypedArray()) }.toTypedArray()))
                .setNumberOfFights(0)
    }

    override fun characteristics(): MutableSet<Collector.Characteristics> {
        return mutableSetOf(Collector.Characteristics.CONCURRENT)
    }

    override fun supplier(): Supplier<StageDescriptorAccumulator> {
        return Supplier { StageDescriptorAccumulator(StageDescriptorDTO()) }
    }

    override fun accumulator(): BiConsumer<StageDescriptorAccumulator, Record> {
        return BiConsumer { t, u ->
            t.stageDescriptorDTO.id = u[StageDescriptor.STAGE_DESCRIPTOR.ID]
            t.stageDescriptorDTO.bracketType = BracketType.valueOf(u[StageDescriptor.STAGE_DESCRIPTOR.BRACKET_TYPE])
            t.stageDescriptorDTO.categoryId = u[StageDescriptor.STAGE_DESCRIPTOR.CATEGORY_ID]
            t.stageDescriptorDTO.competitionId = u[StageDescriptor.STAGE_DESCRIPTOR.COMPETITION_ID]
            t.stageDescriptorDTO.hasThirdPlaceFight = u[StageDescriptor.STAGE_DESCRIPTOR.HAS_THIRD_PLACE_FIGHT]
            t.stageDescriptorDTO.name = u[StageDescriptor.STAGE_DESCRIPTOR.NAME]
            t.stageDescriptorDTO.fightDuration = u[StageDescriptor.STAGE_DESCRIPTOR.FIGHT_DURATION]
            t.stageDescriptorDTO.stageType = StageType.valueOf(u[StageDescriptor.STAGE_DESCRIPTOR.STAGE_TYPE])
            t.stageDescriptorDTO.stageOrder = u[StageDescriptor.STAGE_DESCRIPTOR.STAGE_ORDER]
            t.stageDescriptorDTO.stageStatus = u[StageDescriptor.STAGE_DESCRIPTOR.STAGE_STATUS]?.let { StageStatus.valueOf(it) }
            t.stageDescriptorDTO.waitForPrevious = u[StageDescriptor.STAGE_DESCRIPTOR.WAIT_FOR_PREVIOUS]

            if (!u[FightResultOption.FIGHT_RESULT_OPTION.ID].isNullOrBlank()
                    && t.fightResultOptions.none { it.id == u[FightResultOption.FIGHT_RESULT_OPTION.ID] }) {
                t.fightResultOptions.add(fightResultOption(u))
            }
            if (!u[GroupDescriptor.GROUP_DESCRIPTOR.ID].isNullOrBlank()
                    && t.groups.none { it.id == u[GroupDescriptor.GROUP_DESCRIPTOR.ID] }) {
                t.groups.add(groupDescriptorDTO(u))
            }

            t.stageResults.stageResultDescriptor.id = u[StageDescriptor.STAGE_DESCRIPTOR.ID]
            t.stageResults.stageResultDescriptor.name = u[StageDescriptor.STAGE_DESCRIPTOR.NAME] + " Results"
            t.stageResults.stageResultDescriptor.isForceManualAssignment = u[StageDescriptor.STAGE_DESCRIPTOR.FORCE_MANUAL_ASSIGNMENT]
            t.stageResults.stageResultDescriptor.outputSize = u[StageDescriptor.STAGE_DESCRIPTOR.OUTPUT_SIZE]

            if (t.stageResults.competitorResults.none {
                        it.competitorId == u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.COMPETITOR_ID]
                                && it.stageId == u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.STAGE_ID]
                    }
                    && !u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.STAGE_ID].isNullOrBlank()) {
                t.stageResults.competitorResults.add(competitorStageResultDTO(u))
            }

            if (!u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.STAGE_ID].isNullOrBlank()
                    && u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.GROUP_SORT_SPECIFIER] != null &&
                    t.additionalGroupSorting.none {
                        it.groupSortSpecifier?.name == u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.GROUP_SORT_SPECIFIER]
                    }) {
                t.additionalGroupSorting.add(AdditionalGroupSortingDescriptorDTO()
                        .setGroupSortDirection(u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.GROUP_SORT_DIRECTION]?.let {
                            GroupSortDirection.valueOf(it)
                        })
                        .setGroupSortSpecifier(u[AdditionalGroupSortingDescriptor.ADDITIONAL_GROUP_SORTING_DESCRIPTOR.GROUP_SORT_SPECIFIER]?.let {
                            GroupSortSpecifier.valueOf(it)
                        }))
            }

            t.stageInputs.inputDescriptor.id = u[StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.ID]
            t.stageInputs.inputDescriptor.numberOfCompetitors = u[StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.NUMBER_OF_COMPETITORS]
            t.stageInputs.inputDescriptor.distributionType = u[StageInputDescriptor.STAGE_INPUT_DESCRIPTOR.DISTRIBUTION_TYPE]?.let { DistributionType.valueOf(it) }
            if (t.stageInputs.competitorSelectorAccumulators.none { it.competitorSelector.id == u[CompetitorSelector.COMPETITOR_SELECTOR.ID] }
                    && !u[CompetitorSelector.COMPETITOR_SELECTOR.ID].isNullOrBlank()) {
                t.stageInputs.competitorSelectorAccumulators.add(CompetitorSelectorAccumulator(CompetitorSelectorDTO()
                        .setId(u[CompetitorSelector.COMPETITOR_SELECTOR.ID])
                        .setApplyToStageId(u[CompetitorSelector.COMPETITOR_SELECTOR.APPLY_TO_STAGE_ID])
                        .setClassifier(u[CompetitorSelector.COMPETITOR_SELECTOR.CLASSIFIER]?.let { SelectorClassifier.valueOf(it) })
                        .setLogicalOperator(u[CompetitorSelector.COMPETITOR_SELECTOR.LOGICAL_OPERATOR]?.let { LogicalOperator.valueOf(it) })
                        .setOperator(u[CompetitorSelector.COMPETITOR_SELECTOR.OPERATOR]?.let { OperatorType.valueOf(it) })).apply { selectorValues.add(u[CompetitorSelectorSelectorValue.COMPETITOR_SELECTOR_SELECTOR_VALUE.SELECTOR_VALUE]) })
            } else if (!u[CompetitorSelector.COMPETITOR_SELECTOR.ID].isNullOrBlank()) {
                t.stageInputs.competitorSelectorAccumulators.find { it.competitorSelector.id == u[CompetitorSelector.COMPETITOR_SELECTOR.ID] }
                        ?.selectorValues?.add(u[CompetitorSelectorSelectorValue.COMPETITOR_SELECTOR_SELECTOR_VALUE.SELECTOR_VALUE])
            }

        }
    }

    private fun competitorStageResultDTO(u: Record): CompetitorStageResultDTO {
        return CompetitorStageResultDTO()
                .setStageId(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.STAGE_ID])
                .setCompetitorId(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.COMPETITOR_ID])
                .setPlace(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.PLACE])
                .setGroupId(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.GROUP_ID])
                .setRound(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.ROUND])
                .setPoints(u[CompetitorStageResult.COMPETITOR_STAGE_RESULT.POINTS])
    }

    private fun groupDescriptorDTO(u: Record): GroupDescriptorDTO {
        return GroupDescriptorDTO()
                .setId(u[GroupDescriptor.GROUP_DESCRIPTOR.ID])
                .setName(u[GroupDescriptor.GROUP_DESCRIPTOR.NAME])
                .setSize(u[GroupDescriptor.GROUP_DESCRIPTOR.SIZE])
    }

    private fun fightResultOption(u: Record): FightResultOptionDTO {
        return FightResultOptionDTO()
                .setId(u[FightResultOption.FIGHT_RESULT_OPTION.ID])
                .setWinnerPoints(u[FightResultOption.FIGHT_RESULT_OPTION.WINNER_POINTS])
                .setWinnerAdditionalPoints(u[FightResultOption.FIGHT_RESULT_OPTION.WINNER_ADDITIONAL_POINTS])
                .setLoserPoints(u[FightResultOption.FIGHT_RESULT_OPTION.LOSER_POINTS])
                .setLoserAdditionalPoints(u[FightResultOption.FIGHT_RESULT_OPTION.LOSER_ADDITIONAL_POINTS])
                .setShortName(u[FightResultOption.FIGHT_RESULT_OPTION.SHORT_NAME])
                .setDraw(u[FightResultOption.FIGHT_RESULT_OPTION.DRAW])
                .setDescription(u[FightResultOption.FIGHT_RESULT_OPTION.DESCRIPTION])
    }

    override fun combiner(): BinaryOperator<StageDescriptorAccumulator> {
        return BinaryOperator { t, u ->
            if (t.stageDescriptorDTO.id == u.stageDescriptorDTO.id) {
                StageDescriptorAccumulator(t.stageDescriptorDTO).apply {
                    this.additionalGroupSorting.addAll(t.additionalGroupSorting)
                    this.additionalGroupSorting.addAll(u.additionalGroupSorting)
                    this.fightResultOptions.addAll(t.fightResultOptions)
                    this.fightResultOptions.addAll(u.fightResultOptions)
                    this.groups.addAll(t.groups)
                    this.groups.addAll(u.groups)
                    this.stageInputs.competitorSelectorAccumulators.addAll(t.stageInputs.competitorSelectorAccumulators)
                    this.stageInputs.competitorSelectorAccumulators.addAll(u.stageInputs.competitorSelectorAccumulators)
                    this.stageResults.competitorResults.addAll(t.stageResults.competitorResults)
                    this.stageResults.competitorResults.addAll(u.stageResults.competitorResults)
                }
            } else {
                throw IllegalStateException("Trying to combine accumulators for different stages.")
            }
        }
    }

    override fun finisher(): Function<StageDescriptorAccumulator, StageDescriptorDTO> {
        return Function { createStageDescriptorFromAccumulator(it) }
    }
}