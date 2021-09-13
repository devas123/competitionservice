package compman.compsrv.query.model.mapping

import cats.Monad
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition.CategoryDescriptorDTO
import compman.compsrv.query.model._

object DtoMapping {

  def mapCategoryDescriptor[F[+_]: Monad](competitionId: String)(dto: CategoryDescriptorDTO): F[Category] = {
    Monad[F].pure(Category(
      dto.getId,
      competitionId,
      Option(dto.getRestrictions).map(_.toSet).map(_.map(d =>
        Restriction(
          d.getId,
          d.getType,
          d.getName,
          d.getValue,
          d.getAlias,
          d.getMinValue,
          d.getMaxValue,
          d.getName,
          d.getRestrictionOrder
        )
      )).getOrElse(Set.empty),
      Option(dto.getName),
      dto.getRegistrationOpen
    ))
  }

  def mapStageResultDescriptor(dto: StageResultDescriptorDTO): StageResultDescriptor = {
    StageResultDescriptor(
      dto.getId,
      dto.getName,
      dto.isForceManualAssignment,
      dto.getOutputSize,
      Option(dto.getFightResultOptions).map(_.toSet).map(_.map(mapFightResultOption)).getOrElse(Set.empty),
      Option(dto.getCompetitorResults).map(_.toSet).map(_.map(mapCompetitorStageResult)).getOrElse(Set.empty),
      Option(dto.getAdditionalGroupSortingDescriptors).map(_.toSet).map(_.map(mapAdditionalGroupSortingDescriptor))
        .getOrElse(Set.empty)
    )
  }

  def mapFightResultOption(dto: FightResultOptionDTO): FightResultOption = FightResultOption(
    dto.getId,
    description = dto.getDescription,
    shortName = dto.getShortName,
    draw = dto.isDraw,
    winnerPoints = dto.getWinnerPoints,
    winnerAdditionalPoints = dto.getWinnerAdditionalPoints,
    loserPoints = dto.getLoserPoints,
    loserAdditionalPoints = dto.getLoserAdditionalPoints
  )

  def mapAdditionalGroupSortingDescriptor(dto: AdditionalGroupSortingDescriptorDTO): AdditionalGroupSortingDescriptor =
    AdditionalGroupSortingDescriptor(dto.getGroupSortDirection, dto.getGroupSortSpecifier)

  def mapCompetitorStageResult(dto: CompetitorStageResultDTO): CompetitorStageResult = CompetitorStageResult(
    dto.getCompetitorId,
    dto.getPoints,
    dto.getRound,
    dto.getRoundType,
    dto.getPlace,
    dto.getStageId,
    dto.getGroupId,
    dto.getConflicting
  )

  def mapStageInputDescriptor(d: StageInputDescriptorDTO): StageInputDescriptor = StageInputDescriptor(
    d.getId,
    d.getNumberOfCompetitors,
    Option(d.getSelectors).map(_.toSet).map(_.map(mapCompetitorSelector)).getOrElse(Set.empty),
    d.getDistributionType
  )

  def mapCompetitorSelector(dto: CompetitorSelectorDTO): CompetitorSelector = {
    CompetitorSelector(
      dto.getId,
      dto.getApplyToStageId,
      dto.getLogicalOperator,
      dto.getClassifier,
      dto.getOperator,
      dto.getSelectorValue.toSet
    )
  }

  def mapStageDescriptor[F[+_]: Monad](s: StageDescriptorDTO): F[StageDescriptor] =
    Monad[F].pure {
      StageDescriptor(
        s.getId,
        s.getName,
        s.getCategoryId,
        s.getCompetitionId,
        s.getBracketType,
        s.getStageType,
        s.getStageStatus,
        Option(s.getStageResultDescriptor).map(mapStageResultDescriptor),
        Option(s.getInputDescriptor).map(mapStageInputDescriptor),
        s.getStageOrder,
        s.getWaitForPrevious,
        s.getHasThirdPlaceFight,
        Option(s.getGroupDescriptors).map(_.toSet).map(_.map(dto => GroupDescriptor(dto.getId, Option(dto.getName), dto.getSize))).getOrElse(Set.empty),
        s.getNumberOfFights,
        Option(s.getFightDuration).map(_.longValue()).getOrElse(0L)
      )
    }
}
