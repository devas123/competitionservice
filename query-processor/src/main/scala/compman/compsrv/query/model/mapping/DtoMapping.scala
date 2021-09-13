package compman.compsrv.query.model.mapping

import cats.Monad
import cats.data.OptionT
import compman.compsrv.model.dto.brackets._
import compman.compsrv.query.model._

trait DtoMapping[F[+_]] {
  def mapStageResultDescriptor(s: StageResultDescriptorDTO): F[StageResultDescriptor]
}

object DtoMapping {
  def apply[F[+_]](implicit F: DtoMapping[F]): DtoMapping[F] = F

  def mapStageResultDescriptor[F[+_] : Monad](dto: StageResultDescriptorDTO): F[StageResultDescriptor] =
    Monad[F].pure {
      StageResultDescriptor(
        dto.getId,
        dto.getName,
        dto.isForceManualAssignment,
        dto.getOutputSize,
        Option(dto.getFightResultOptions).map(_.toSet).map(_.map(mapFightResultOption)).getOrElse(Set.empty),
        Option(dto.getCompetitorResults).map(_.toSet).map(_.map(mapCompetitorStageResult)).getOrElse(Set.empty),
        Option(dto.getAdditionalGroupSortingDescriptors).map(_.toSet).map(_.map(mapAdditionalGroupSortingDescriptor)).getOrElse(Set.empty)
      )
    }

  def mapFightResultOption(dto: FightResultOptionDTO): FightResultOption =
    FightResultOption(
      dto.getId, description = dto.getDescription, shortName = dto.getShortName, draw = dto.isDraw, winnerPoints = dto.getWinnerPoints, winnerAdditionalPoints = dto.getWinnerAdditionalPoints, loserPoints = dto.getLoserPoints, loserAdditionalPoints = dto.getLoserAdditionalPoints
    )

  def mapAdditionalGroupSortingDescriptor(dto: AdditionalGroupSortingDescriptorDTO): AdditionalGroupSortingDescriptor =
    AdditionalGroupSortingDescriptor(dto.getGroupSortDirection, dto.getGroupSortSpecifier)


  def mapCompetitorStageResult(dto: CompetitorStageResultDTO): CompetitorStageResult =
    CompetitorStageResult(
      dto.getCompetitorId,
      dto.getPoints,
      dto.getRound,
      dto.getRoundType,
      dto.getPlace,
      dto.getStageId,
      dto.getGroupId,
      dto.getConflicting
    )

  def mapStageInputDescriptor[F[+_] : Monad](d: StageInputDescriptorDTO): F[StageInputDescriptor] = Monad[F].pure {

    StageInputDescriptor(
      d.getId,
      d.getNumberOfCompetitors,
      Option(d.getSelectors).map(_.toSet).map(_.map(mapCompetitorSelector)).getOrElse(Set.empty),
      d.getDistributionType
    )
  }

  def mapCompetitorSelector(dto: CompetitorSelectorDTO): CompetitorSelector = {
    CompetitorSelector(dto.getId, dto.getApplyToStageId, dto.getLogicalOperator, dto.getClassifier, dto.getOperator, dto.getSelectorValue.toSet)
  }

  def mapStageDescriptor[F[+_] : Monad](s: StageDescriptorDTO): F[Option[StageDescriptor]] = {
    for {
      srdDto <- OptionT.fromOption[F](Option(s.getStageResultDescriptor))
      idDto <- OptionT.fromOption[F](Option(s.getInputDescriptor))
      inputDescr <- OptionT.liftF(mapStageInputDescriptor(idDto))
      srd <- OptionT.liftF(mapStageResultDescriptor(srdDto))
      res = StageDescriptor(
        s.getId,
        s.getName,
        s.getCategoryId,
        s.getCompetitionId,
        s.getBracketType,
        s.getStageType,
        s.getStageStatus,
        Some(srd),
        Some(inputDescr),
        s.getStageOrder,
        s.getWaitForPrevious,
        s.getHasThirdPlaceFight,
        s.getGroupDescriptors.toSet.map(dto => GroupDescriptor(dto.getId, Option(dto.getName), dto.getSize)),
        s.getNumberOfFights,
        s.getFightDuration
      )

    } yield res
  }.value

}
