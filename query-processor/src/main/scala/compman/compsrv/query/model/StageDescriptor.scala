package compman.compsrv.query.model

import compman.compsrv.model.dto.brackets._

case class StageDescriptor(
  id: String,
  name: Option[String],
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
  groupDescriptors: Option[List[GroupDescriptor]],
  numberOfFights: Option[Int],
  fightDuration: Option[Long]
)

case class StageResultDescriptor(
  name: String,
  forceManualAssignment: Boolean,
  outputSize: Int,
  fightResultOptions: List[FightResultOption],
  competitorResults: List[CompetitorStageResult],
  additionalGroupSortingDescriptors: List[AdditionalGroupSortingDescriptor]
)

case class AdditionalGroupSortingDescriptor(
  groupSortDirection: GroupSortDirection,
  groupSortSpecifier: GroupSortSpecifier
)

case class CompetitorStageResult(
  competitorId: String,
  points: Int,
  round: Int,
  roundType: StageRoundType,
  place: Int,
  stageId: String,
  groupId: String,
  conflicting: Boolean
)

case class FightResultOption(
                              optionId: String,
                              description: String,
                              shortName: String,
                              draw: Boolean,
                              winnerPoints: Int,
                              winnerAdditionalPoints: Int = 0,
                              loserPoints: Int = 0,
                              loserAdditionalPoints: Int = 0
)

case class CompetitorSelector(
                               selectorId: String,
                               applyToStageId: String,
                               logicalOperator: LogicalOperator,
                               classifier: SelectorClassifier,
                               operator: OperatorType,
                               selectorValue: Set[String]
)

case class StageInputDescriptor(
  numberOfCompetitors: Int,
  selectors: List[CompetitorSelector],
  distributionType: DistributionType
)

case class GroupDescriptor(groupId: String, name: Option[String], size: Int)
