package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CategoryRestrictionType, CompetitionStatus}
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate

import java.time.Instant

trait TestEntities {
  private[repository] val competitionId = "managedCompetition"
  private[repository] val categoryId    = "test-category"
  val managedCompetition: ManagedCompetition = ManagedCompetition(
    "competitionId",
    "ecompetition-id-topic",
    "valera_protas",
    Instant.now(),
    Instant.now(),
    Some(Instant.now()),
    "UTC",
    CompetitionStatus.CREATED
  )

  val competitionProperties: CompetitionProperties = CompetitionProperties(
    competitionId,
    "creatorId",
    Some(Set("a", "b", "c")),
    "Some competition",
    CompetitionInfoTemplate("superlongdescriptionblob".getBytes),
    Instant.now(),
    schedulePublished = false,
    bracketsPublished = false,
    Some(Instant.now()),
    "UTC",
    registrationOpen = true,
    Instant.now(),
    CompetitionStatus.CREATED
  )
  val stageResultDescriptor: StageResultDescriptor = StageResultDescriptor(
    "what's up",
    forceManualAssignment = false,
    10,
    List(
      FightResultOption("frO1", "description1", "die", draw = false, 199, 3399, 0, -1),
      FightResultOption("frO2", "description2", "live", draw = false, 2, 3, 9, 4)
    ),
    List(CompetitorStageResult(
      "competitor",
      10,
      3,
      StageRoundType.WINNER_BRACKETS,
      10,
      "stageId",
      "group",
      conflicting = true
    )),
    List(AdditionalGroupSortingDescriptor(GroupSortDirection.DESC, GroupSortSpecifier.POINTS_DIFFERENCE))
  )

  val stageInputDescriptor: StageInputDescriptor = StageInputDescriptor(
    15,
    List(CompetitorSelector(
      "aid",
      "applyToStage",
      LogicalOperator.AND,
      SelectorClassifier.FIRST_N_PLACES,
      OperatorType.LESS,
      Set("a", "b", "c")
    )),
    DistributionType.AUTOMATIC
  )
  val restriction: Restriction = Restriction(
    "restrictionId",
    CategoryRestrictionType.Range,
    Some("name"),
    Some("a"),
    Some("b"),
    Some("c"),
    Some("d"),
    Some("e"),
    0
  )

  val category: Category =
    Category(categoryId, competitionId, List(restriction), Some("categoryName"), registrationOpen = true)

  val stageDescriptor: StageDescriptor = StageDescriptor(
    s"$competitionId-stage",
    Some("test-stage-descriptor"),
    categoryId,
    competitionId,
    BracketType.SINGLE_ELIMINATION,
    StageType.FINAL,
    StageStatus.APPROVED,
    Option(stageResultDescriptor),
    Option(stageInputDescriptor),
    0,
    waitForPrevious = false,
    hasThirdPlaceFight = true,
    Some(List(GroupDescriptor("gr1", Some("gr1-name"), 100))),
    Some(100),
    Some(10)
  )
}
