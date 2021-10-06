package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.CompetitionStatus
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

  private[repository] val competitionProperties = CompetitionProperties(
    competitionId,
    "creatorId",
    Set("a", "b", "c"),
    "Some competition",
    CompetitionInfoTemplate("superlongdescriptionblob".getBytes),
    Instant.now(),
    schedulePublished = false,
    bracketsPublished = false,
    Instant.now(),
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

  val stageDescriptor: StageDescriptor = StageDescriptor(
    s"$competitionId-stage",
    "test-stage-descriptor",
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
    List(
      GroupDescriptor(
        "gr1", Some("gr1-name"), 100
      )
    ),
    100,
    10
  )
}
