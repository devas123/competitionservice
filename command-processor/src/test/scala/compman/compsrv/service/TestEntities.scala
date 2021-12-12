package compman.compsrv.service

import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO

import scala.jdk.CollectionConverters.CollectionHasAsScala

trait TestEntities {

  val competitionId = "managedCompetition"
  val categoryId    = "test-category"
  val stageId       = "stage-id"
  val matId: String = "mat-id"
  val periodId = "period-id"

  val groupIdFormat = "group-id-%s"
  val groupRange: Range.Inclusive     = 0 to 3
  val startingCompetitorsSizeForGroup = 5

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

  val fights = List(
    new FightDescriptionDTO(
      "fight1",
      categoryId,
      "Semi-final",
      "fight3",
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
      null,
      null,
      0,
      competitionId,
      null,
      null,
      stageId,
      null,
      null,
      1
    ),
    new FightDescriptionDTO(
      "fight2",
      categoryId,
      "Semi-final",
      "fight3",
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
      null,
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
      "fight3",
      categoryId,
      "Final",
      null,
      null,
      Array(
        new CompScoreDTO(null, null, new ScoreDTO(0, 0, 0, Array.empty), 0, FightReferenceType.WINNER, "fight2"),
        new CompScoreDTO(null, null, new ScoreDTO(0, 0, 0, Array.empty), 1, FightReferenceType.WINNER, "fight1")
      ),
      BigDecimal(10).bigDecimal,
      1,
      false,
      StageRoundType.GRAND_FINAL,
      FightStatus.PENDING,
      null,
      null,
      null,
      0,
      competitionId,
      null,
      null,
      stageId,
      null,
      null,
      1
    )
  )

}
