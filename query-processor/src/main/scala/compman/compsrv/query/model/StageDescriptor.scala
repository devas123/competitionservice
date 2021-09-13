package compman.compsrv.query.model

import compman.compsrv.model.dto.brackets.{BracketType, DistributionType, GroupSortDirection, GroupSortSpecifier, LogicalOperator, OperatorType, SelectorClassifier, StageRoundType, StageStatus, StageType}
import io.getquill.Udt

case class StageDescriptor(
                            id: String,
                            name: String,
                            categoryId: String,
                            competitionId: String,
                            bracketType: BracketType,
                            stageType: StageType,
                            stageStatus: StageStatus,
                            stageResultDescriptor: Option[StageResultDescriptor],
                            inputDescriptor: Option[StageInputDescriptor],
                            stageOrder: Int,
                            waitForPrevious: Boolean,
                            hasThirdPlaceFight: Boolean,
                            groupDescriptors: Set[GroupDescriptor],
                            numberOfFights: Int,
                            fightDuration: Long)

case class StageResultDescriptor(
                                  id: String,
                                  name: String,
                                  forceManualAssignment: Boolean,
                                  outputSize: Int,
                                  fightResultOptions: Set[FightResultOption],
                                  competitorResults: Set[CompetitorStageResult],
                                  additionalGroupSortingDescriptors: Set[AdditionalGroupSortingDescriptor],
                                ) extends Udt

case class AdditionalGroupSortingDescriptor(
                                             groupSortDirection: GroupSortDirection,
                                             groupSortSpecifier: GroupSortSpecifier
                                           ) extends Udt

case class CompetitorStageResult(
                                  competitorId: String,
                                  points: Int,
                                  round: Int,
                                  roundType: StageRoundType,
                                  place: Int,
                                  stageId: String,
                                  groupId: String,
                                  conflicting: Boolean,
                                ) extends Udt

case class FightResultOption(
                              id: String,
                              description: String,
                              shortName: String,
                              draw: Boolean,
                              winnerPoints: Int,
                              winnerAdditionalPoints: Int = 0,
                              loserPoints: Int = 0,
                              loserAdditionalPoints: Int = 0,
                            ) extends Udt

case class CompetitorSelector(
                               id: String,
                               applyToStageId: String,
                               logicalOperator: LogicalOperator,
                               classifier: SelectorClassifier,
                               operator: OperatorType,
                               selectorValue: Set[String],
                             ) extends Udt

case class StageInputDescriptor(
                                 id: String,
                                 numberOfCompetitors: Int,
                                 selectors: Set[CompetitorSelector],
                                 distributionType: DistributionType,

                               ) extends Udt


case class GroupDescriptor(id: String, name: Option[String], size: Int) extends Udt
