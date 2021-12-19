package compman.compsrv.service

import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO

import scala.jdk.CollectionConverters.CollectionHasAsScala

trait TestEntities {
  final val fight2 = "fight2"
  final val fight1 = "fight1"
  final val fight3 = "fight3"
  final val mat1Id = "mat1"
  final val mat2Id = "mat2"
  final val competitionId = "managedCompetition"
  final val categoryId = "test-category"
  final val stageId = "stage-id"
  final val matId: String = "mat-id"
  final val periodId = "period-id"

  final val groupIdFormat = "group-id-%s"
  final val groupRange: Range.Inclusive = 0 to 3
  final val startingCompetitorsSizeForGroup = 5

  val groupDescriptors: IndexedSeq[GroupDescriptorDTO] = groupRange map { groupIndex =>
    new GroupDescriptorDTO().setId(groupIdFormat.format(groupIndex)).setName(groupIdFormat.format(groupIndex))
      .setSize(startingCompetitorsSizeForGroup + groupIndex)
  }

  val testMat: MatDescriptionDTO = new MatDescriptionDTO()
    .setId(matId)
    .setName("Test mat")
    .setPeriodId(periodId)
    .setMatOrder(0)

  val totalNumberOfCompetitors: Int = groupRange.map(_ + startingCompetitorsSizeForGroup).sum

  val inputDescriptor: StageInputDescriptorDTO = new StageInputDescriptorDTO().setSelectors(Array.empty)
    .setNumberOfCompetitors(totalNumberOfCompetitors).setDistributionType(DistributionType.AUTOMATIC)

  val resultRescriptor: StageResultDescriptorDTO = new StageResultDescriptorDTO().setName(stageId)
    .setCompetitorResults(Array.empty).setFightResultOptions(FightResultOptionDTO.values.asScala.toArray)
    .setOutputSize(1).setAdditionalGroupSortingDescriptors(Array.empty).setForceManualAssignment(false)

  val stageForGroupsGeneration: StageDescriptorDTO = new StageDescriptorDTO().setId(stageId).setName(stageId)
    .setCompetitionId(competitionId).setStageType(StageType.FINAL).setStageOrder(0).setBracketType(BracketType.GROUP)
    .setFightDuration(BigDecimal.decimal(10).bigDecimal).setGroupDescriptors(groupDescriptors.toArray)
    .setInputDescriptor(inputDescriptor).setStageResultDescriptor(resultRescriptor)
    .setStageStatus(StageStatus.WAITING_FOR_APPROVAL).setWaitForPrevious(false)

  val competitors = List(
    new CompetitorDTO().setId("competitor1").setCategories(Array(categoryId)),
    new CompetitorDTO().setId("competitor2").setCategories(Array(categoryId)),
    new CompetitorDTO().setId("competitor3").setCategories(Array(categoryId))
  )


  val mat1: MatDescriptionDTO = new MatDescriptionDTO()
    .setId(mat1Id)
    .setName("Mat 1")
    .setMatOrder(0)
    .setPeriodId(periodId)
  val mat2: MatDescriptionDTO = new MatDescriptionDTO()
    .setId(mat2Id)
    .setName("Mat 2")
    .setMatOrder(1)
    .setPeriodId(periodId)
  val fights = List(
    new FightDescriptionDTO(
      fight1,
      categoryId,
      "Semi-final",
      fight3,
      null,
      Array(
        new CompScoreDTO(
          null,
          "competitor1",
          new ScoreDTO(0, 0, 0, Array.empty),
          0,
          FightReferenceType.PROPAGATED,
          null
        ),
        new CompScoreDTO(
          null,
          "competitor2",
          new ScoreDTO(0, 0, 0, Array.empty),
          1,
          FightReferenceType.PROPAGATED,
          null
        )
      ),
      BigDecimal(10).bigDecimal,
      0,
      false,
      StageRoundType.WINNER_BRACKETS,
      FightStatus.PENDING,
      null,
      mat2,
      null,
      0,
      competitionId,
      periodId,
      null,
      stageId,
      null,
      null,
      1
    ),
    new FightDescriptionDTO(
      fight2,
      categoryId,
      "Semi-final",
      fight3,
      null,
      Array(
        new CompScoreDTO(
          null,
          "competitor3",
          new ScoreDTO(0, 0, 0, Array.empty),
          0,
          FightReferenceType.PROPAGATED,
          null
        ),
        new CompScoreDTO(null, null, new ScoreDTO(0, 0, 0, Array.empty), 1, FightReferenceType.PROPAGATED, null)
      ),
      BigDecimal(10).bigDecimal,
      0,
      false,
      StageRoundType.WINNER_BRACKETS,
      FightStatus.UNCOMPLETABLE,
      null,
      mat1,
      null,
      0,
      competitionId,
      null,
      null,
      stageId,
      null,
      null,
      0
    ),
    new FightDescriptionDTO(
      fight3,
      categoryId,
      "Final",
      null,
      null,
      Array(
        new CompScoreDTO(null, null, new ScoreDTO(0, 0, 0, Array.empty), 0, FightReferenceType.WINNER, fight2),
        new CompScoreDTO(null, null, new ScoreDTO(0, 0, 0, Array.empty), 1, FightReferenceType.WINNER, fight1)
      ),
      BigDecimal(10).bigDecimal,
      1,
      false,
      StageRoundType.GRAND_FINAL,
      FightStatus.PENDING,
      null,
      mat1,
      null,
      0,
      competitionId,
      periodId,
      null,
      stageId,
      null,
      null,
      1
    )
  )

}
