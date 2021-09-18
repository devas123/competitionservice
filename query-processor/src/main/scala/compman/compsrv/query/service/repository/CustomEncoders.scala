package compman.compsrv.query.service.repository

import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.{CategoryRestrictionType, CompetitionStatus}
import io.getquill.MappedEncoding

trait CustomEncoders {
  implicit val competitionStatusEncoder: MappedEncoding[CompetitionStatus, String] =
    MappedEncoding[CompetitionStatus, String](_.name())

  implicit val distributionTypeEncoder: MappedEncoding[DistributionType, String] =
    MappedEncoding[DistributionType, String](_.name())
  implicit val stageRoundTypeEncoder: MappedEncoding[StageRoundType, String] =
    MappedEncoding[StageRoundType, String](_.name())
  implicit val groupSortDirectionEncoder: MappedEncoding[GroupSortDirection, String] =
    MappedEncoding[GroupSortDirection, String](_.name())
  implicit val logicalOperatorEncoder: MappedEncoding[LogicalOperator, String] =
    MappedEncoding[LogicalOperator, String](_.name())
  implicit val groupSortSpecifierEncoder: MappedEncoding[GroupSortSpecifier, String] =
    MappedEncoding[GroupSortSpecifier, String](_.name())
  implicit val selectorClassSpecifierEncoder: MappedEncoding[SelectorClassifier, String] =
    MappedEncoding[SelectorClassifier, String](_.name())
  implicit val operatorTypeEncoder: MappedEncoding[OperatorType, String] =
    MappedEncoding[OperatorType, String](_.name())
  implicit val bracketTypeEncoder: MappedEncoding[BracketType, String] = MappedEncoding[BracketType, String](_.name())
  implicit val stageTypeEncoder: MappedEncoding[StageType, String]     = MappedEncoding[StageType, String](_.name())
  implicit val stageStatusEncoder: MappedEncoding[StageStatus, String] = MappedEncoding[StageStatus, String](_.name())
  implicit val categoryRestrictionTypeEncoder: MappedEncoding[CategoryRestrictionType, String] =
    MappedEncoding[CategoryRestrictionType, String](_.name())
  implicit val fightReferenceTypeEncoder: MappedEncoding[FightReferenceType, String] =
    MappedEncoding[FightReferenceType, String](_.name())

}
