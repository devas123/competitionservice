package compman.compsrv.query.service.repository

import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import compservice.model.protobuf.model.{BracketType, CategoryRestrictionType, CompetitionStatus, DistributionType, FightReferenceType, FightStatus, GroupSortDirection, GroupSortSpecifier, LogicalOperator, OperatorType, SelectorClassifier, StageRoundType, StageStatus, StageType}

import java.time.Instant
import java.util.Date

trait TestEntities {
  private[repository] val competitionId = "managedCompetition"
  private[repository] val stageId       = s"$competitionId-stage"
  private[repository] val categoryId    = "test-category"
  private[repository] val matId         = "mat_id"
  private[repository] val periodId      = "period_id"
  val managedCompetition: ManagedCompetition = ManagedCompetition(
    "competitionId",
    Option("competitionName"),
    "ecompetition-id-topic",
    Option("valera_protas"),
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
    Date.from(Instant.now()),
    schedulePublished = false,
    bracketsPublished = false,
    Some(Instant.now()).map(Date.from),
    "UTC",
    Date.from(Instant.now()),
    CompetitionStatus.CREATED
  )
  val stageResultDescriptor: StageResultDescriptor = StageResultDescriptor(
    Option("what's up"),
    forceManualAssignment = false,
    10,
    List(
      FightResultOption("frO1", Option("description1"), Option("die"), draw = false, 199, 3399, 0, -1),
      FightResultOption("frO2", Option("description2"), Option("live"), draw = false, 2, 3, 9, 4)
    ),
    List(CompetitorStageResult(
      "competitor",
      10,
      3,
      StageRoundType.WINNER_BRACKETS,
      10,
      "stageId",
      Option("group"),
      conflicting = true
    )),
    List(AdditionalGroupSortingDescriptor(GroupSortDirection.DESC, GroupSortSpecifier.POINTS_DIFFERENCE))
  )

  val stageInputDescriptor: StageInputDescriptor = StageInputDescriptor(
    15,
    List(CompetitorSelector(
      "applyToStage",
      LogicalOperator.AND,
      SelectorClassifier.FIRST_N_PLACES,
      OperatorType.LESS,
      Set("a", "b", "c")
    )),
    DistributionType.AUTOMATIC
  )

  val fightId: String = "fight_id"

  val fight: Fight = Fight(
    id = fightId,
    fightName = Some("fightName"),
    competitionId = competitionId,
    stageId = stageId,
    categoryId = categoryId,
    matId = Some(matId),
    matName = None,
    matOrder = Some(0),
    durationSeconds = 300,
    status = Some(FightStatus.PENDING),
    numberOnMat = Some(0),
    periodId = Option(periodId),
    startTime = None,
    invalid = Option(false),
    scheduleEntryId = Option("scheduleEntry1"),
    priority = None,
    bracketsInfo = None,
    fightResult = None,
    scores = List.empty
  )

  val fightResult: FightResult = FightResult(
    winnerId = Option("competitor1"), resultTypeId = Option("WinByPoints"), reason = Some("For some reason")
  )

  val scores: Seq[CompScore] = List(CompScore(
    placeholderId = None,
    competitorId = Some("competitor1"),
    competitorFirstName = Some("Vasya"),
    competitorLastName = Some("Pupkin"),
    competitorAcademyName = Some("Bor"),
    score = Score(
      points = 0, advantages = 0, penalties = 0, pointGroups = List.empty
    ),
    parentReferenceType = Some(FightReferenceType.WINNER),
    parentFightId = Some("parentFight1")
  ),
    CompScore(
      placeholderId = None,
      competitorId = Some("competitor2"),
      competitorFirstName = Some("Kolya"),
      competitorLastName = Some("Meklinsky"),
      competitorAcademyName = Some("Gatchina"),
      score = Score(
        points = 0, advantages = 1, penalties = 0, pointGroups = List.empty
      ),
      parentReferenceType = Some(FightReferenceType.LOSER),
      parentFightId = Some("parentFight2")
    ))

  val restriction: Restriction = Restriction(
    "restrictionId",
    CategoryRestrictionType.RANGE,
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
    stageId,
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
