package compman.compsrv.service

import compman.compsrv.logic.fight.FightResultOptionConstants
import compservice.model.protobuf.model._

trait TestEntities {
  final val fight2        = "fight2"
  final val fight1        = "fight1"
  final val fight3        = "fight3"
  final val mat1Id        = "mat1"
  final val mat2Id        = "mat2"
  final val competitionId = "managedCompetition"
  final val categoryId    = "test-category"
  final val stageId       = "stage-id"
  final val matId: String = "mat-id"
  final val periodId      = "period-id"

  final val groupIdFormat                   = "group-id-%s"
  final val groupRange: Range.Inclusive     = 0 to 3
  final val startingCompetitorsSizeForGroup = 5

  val groupDescriptors: IndexedSeq[GroupDescriptor] = groupRange map { groupIndex =>
    GroupDescriptor().withId(groupIdFormat.format(groupIndex)).withName(groupIdFormat.format(groupIndex))
      .withSize(startingCompetitorsSizeForGroup + groupIndex)
  }

  val testMat: MatDescription = MatDescription().withId(matId).withName("Test mat").withPeriodId(periodId)
    .withMatOrder(0)

  val totalNumberOfCompetitors: Int = groupRange.map(_ + startingCompetitorsSizeForGroup).sum

  val inputDescriptor: StageInputDescriptor = StageInputDescriptor().withSelectors(Array.empty)
    .withNumberOfCompetitors(totalNumberOfCompetitors).withDistributionType(DistributionType.AUTOMATIC)

  val resultDescriptor: StageResultDescriptor = StageResultDescriptor().withName(stageId)
    .withCompetitorResults(Array.empty).withFightResultOptions(FightResultOptionConstants.values).withOutputSize(1)

  val stageForGroupsGeneration: StageDescriptor = StageDescriptor().withId(stageId).withName(stageId)
    .withCompetitionId(competitionId).withStageType(StageType.FINAL).withStageOrder(0)
    .withBracketType(BracketType.GROUP).withFightDuration(600).withGroupDescriptors(groupDescriptors)
    .withInputDescriptor(inputDescriptor).withStageResultDescriptor(
      resultDescriptor.withAdditionalGroupSortingDescriptors(Array.empty).withForceManualAssignment(false)
    ).withStageStatus(StageStatus.WAITING_FOR_APPROVAL).withWaitForPrevious(false)

  val singleEliminationBracketsStage: StageDescriptor = StageDescriptor().withId(stageId).withName(stageId)
    .withCompetitionId(competitionId).withStageType(StageType.FINAL).withStageOrder(0)
    .withBracketType(BracketType.SINGLE_ELIMINATION).withFightDuration(600).withInputDescriptor(inputDescriptor)
    .withStageResultDescriptor(resultDescriptor).withStageStatus(StageStatus.APPROVED).withWaitForPrevious(false)

  val competitors: Seq[Competitor] = List(
    Competitor().withId("competitor1").withCategories(Array(categoryId)),
    Competitor().withId("competitor2").withCategories(Array(categoryId)),
    Competitor().withId("competitor3").withCategories(Array(categoryId))
  )

  val mat1: MatDescription = MatDescription().withId(mat1Id).withName("Mat 1").withMatOrder(0).withPeriodId(periodId)
  val mat2: MatDescription = MatDescription().withId(mat2Id).withName("Mat 2").withMatOrder(1).withPeriodId(periodId)
  val fights: Seq[FightDescription] = List(
    FightDescription(
      fight1,
      categoryId,
      Some("Semi-final"),
      Some(fight3),
      None,
      Array(
        CompScore(
          None,
          Some("competitor1"),
          Some(Score(0, 0, 0, Array.empty)),
          0,
          Some(FightReferenceType.PROPAGATED),
          None
        ),
        CompScore(
          None,
          Some("competitor2"),
          Some(Score(0, 0, 0, Array.empty)),
          1,
          Some(FightReferenceType.PROPAGATED),
          None
        )
      ),
      600,
      0,
      invalid = false,
      StageRoundType.WINNER_BRACKETS,
      FightStatus.PENDING,
      None,
      mat2,
      None,
      0,
      competitionId,
      periodId,
      None,
      stageId,
      None,
      None,
      1
    ),
    FightDescription(
      fight2,
      categoryId,
      Some("Semi-final"),
      Some(fight3),
      None,
      Array(
        CompScore(
          None,
          Some("competitor3"),
          Some(Score(0, 0, 0, Array.empty)),
          0,
          Some(FightReferenceType.PROPAGATED),
          None
        ),
        CompScore(None, None, Some(Score(0, 0, 0, Array.empty)), 1, Some(FightReferenceType.PROPAGATED), None)
      ),
      600,
      0,
      invalid = false,
      StageRoundType.WINNER_BRACKETS,
      FightStatus.UNCOMPLETABLE,
      None,
      mat1,
      None,
      0,
      competitionId,
      None,
      None,
      stageId,
      None,
      None
    ),
    FightDescription(
      fight3,
      categoryId,
      Some("Final"),
      None,
      None,
      Seq(
        CompScore(None, None, Some(Score(0, 0, 0, Seq.empty)), 0, Some(FightReferenceType.WINNER), Some(fight2)),
        CompScore(None, None, Some(Score(0, 0, 0, Seq.empty)), 1, Some(FightReferenceType.WINNER), Some(fight1))
      ),
      600,
      1,
      invalid = false,
      StageRoundType.GRAND_FINAL,
      FightStatus.PENDING,
      None,
      Some(mat1),
      None,
      None,
      competitionId,
      Some(periodId),
      None,
      stageId,
      None,
      None,
      1
    )
  )

}
