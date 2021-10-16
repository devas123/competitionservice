package compman.compsrv.service

import compman.compsrv.model.dto.brackets.{BracketType, DistributionType, FightResultOptionDTO, GroupDescriptorDTO, StageDescriptorDTO, StageInputDescriptorDTO, StageResultDescriptorDTO, StageStatus, StageType}

import scala.jdk.CollectionConverters.CollectionHasAsScala

trait TestEntities {
  val competitionId = "managedCompetition"
  val categoryId    = "test-category"
  val stageId    = "stage-id"
  val groupIdFormat    = "group-id-%s"

  val groupRange: Range.Inclusive = 0 to 3
  val startingCompetitorsSizeForGroup = 5
  val groupDescriptors: IndexedSeq[GroupDescriptorDTO] = groupRange map { groupIndex =>
    new GroupDescriptorDTO()
      .setId(groupIdFormat.format(groupIndex))
      .setName(groupIdFormat.format(groupIndex))
      .setSize(startingCompetitorsSizeForGroup + groupIndex)
  }

  val totalNumberOfCompetitors: Int = groupRange.map(_ + startingCompetitorsSizeForGroup).sum

  val inputDescriptor: StageInputDescriptorDTO = new StageInputDescriptorDTO()
    .setId(stageId)
    .setSelectors(Array.empty)
    .setNumberOfCompetitors(totalNumberOfCompetitors)
    .setDistributionType(DistributionType.AUTOMATIC)

  val resultRescriptor: StageResultDescriptorDTO = new StageResultDescriptorDTO()
    .setId(stageId)
    .setName(stageId)
    .setCompetitorResults(Array.empty)
    .setFightResultOptions(FightResultOptionDTO.values.asScala.toArray)
    .setOutputSize(1)
    .setAdditionalGroupSortingDescriptors(Array.empty)
    .setForceManualAssignment(false)

  val stageForGroupsGeneration: StageDescriptorDTO = new StageDescriptorDTO()
    .setId(stageId)
    .setName(stageId)
    .setCompetitionId(competitionId)
    .setStageType(StageType.FINAL)
    .setStageOrder(0)
    .setBracketType(BracketType.GROUP)
    .setFightDuration(BigDecimal.decimal(10).bigDecimal)
    .setGroupDescriptors(groupDescriptors.toArray)
    .setInputDescriptor(inputDescriptor)
    .setStageResultDescriptor(resultRescriptor)
    .setStageStatus(StageStatus.WAITING_FOR_APPROVAL)
    .setWaitForPrevious(false)

}

