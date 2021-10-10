package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CategoryRestrictionType, CompetitionStatus, CompetitorRegistrationStatus, FightStatus}
import compman.compsrv.model.dto.schedule.{ScheduleEntryType, ScheduleRequirementType}
import io.getquill.MappedEncoding

trait CustomDecoders {
  implicit val competitionStatusDecoder: MappedEncoding[String, CompetitionStatus] =
    MappedEncoding[String, CompetitionStatus](CompetitionStatus.valueOf)
  implicit val distributionTypeDecoder: MappedEncoding[String, DistributionType] =
    MappedEncoding[String, DistributionType](DistributionType.valueOf)
  implicit val stageRoundTypeDecoder: MappedEncoding[String, StageRoundType] =
    MappedEncoding[String, StageRoundType](StageRoundType.valueOf)
  implicit val groupSortDirectionDecoder: MappedEncoding[String, GroupSortDirection] =
    MappedEncoding[String, GroupSortDirection](GroupSortDirection.valueOf)
  implicit val logicalOperatorDecoder: MappedEncoding[String, LogicalOperator] =
    MappedEncoding[String, LogicalOperator](LogicalOperator.valueOf)

  implicit val groupSortSpecifierDecoder: MappedEncoding[String, GroupSortSpecifier] =
    MappedEncoding[String, GroupSortSpecifier](GroupSortSpecifier.valueOf)
  implicit val selectorClassSpecifierDecoder: MappedEncoding[String, SelectorClassifier] =
    MappedEncoding[String, SelectorClassifier](SelectorClassifier.valueOf)
  implicit val operatorTypeDecoder: MappedEncoding[String, OperatorType] =
    MappedEncoding[String, OperatorType](OperatorType.valueOf)
  implicit val bracketTypeDecoder: MappedEncoding[String, BracketType] =
    MappedEncoding[String, BracketType](BracketType.valueOf)
  implicit val stageTypeDecoder: MappedEncoding[String, StageType] =
    MappedEncoding[String, StageType](StageType.valueOf)
  implicit val stageStatusDecoder: MappedEncoding[String, StageStatus] =
    MappedEncoding[String, StageStatus](StageStatus.valueOf)
  implicit val categoryRestrictionTypeDecoder: MappedEncoding[String, CategoryRestrictionType] =
    MappedEncoding[String, CategoryRestrictionType](CategoryRestrictionType.valueOf)
  implicit val fightReferenceTypeDecoder: MappedEncoding[String, FightReferenceType] =
    MappedEncoding[String, FightReferenceType](FightReferenceType.valueOf)
  implicit val scheduleEntryTypeDecoder: MappedEncoding[String, ScheduleEntryType] =
    MappedEncoding[String, ScheduleEntryType](ScheduleEntryType.valueOf)
  implicit val scheduleRequirementTypeDecoder: MappedEncoding[String, ScheduleRequirementType] =
    MappedEncoding[String, ScheduleRequirementType](ScheduleRequirementType.valueOf)
  implicit val competitorRegistrationStatusDecoder: MappedEncoding[String, CompetitorRegistrationStatus] =
    MappedEncoding[String, CompetitorRegistrationStatus](CompetitorRegistrationStatus.valueOf)
  implicit val fightStatusDecoder: MappedEncoding[String, FightStatus] =
    MappedEncoding[String, FightStatus](FightStatus.valueOf)
}
