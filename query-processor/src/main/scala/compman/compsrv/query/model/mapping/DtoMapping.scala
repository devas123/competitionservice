package compman.compsrv.query.model.mapping

import cats.Monad
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.{PeriodDTO, ScheduleEntryDTO, ScheduleRequirementDTO}
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate

object DtoMapping {
  def createEmptyScore: ScoreDTO = new ScoreDTO().setAdvantages(0).setPenalties(0).setPoints(0)
    .setPointGroups(Array.empty)

  def mapScheduleEntry(competitionId: String)(dto: ScheduleEntryDTO): ScheduleEntry = {
    ScheduleEntry(
      id = dto.getId,
      competitionId = competitionId,
      categoryIds = Option(dto.getCategoryIds).map(_.toSet).getOrElse(Set.empty),
      fightIds = Option(dto.getFightIds).map(_.toList).orElse(Option(List.empty))
        .map(_.map(d => MatIdAndSomeId(d.getMatId, d.getSomeId, Option(d.getStartTime)))).get,
      periodId = dto.getPeriodId,
      description = Option(dto.getDescription),
      name = Option(dto.getName),
      color = Option(dto.getColor),
      entryType = dto.getEntryType,
      requirementIds = Option(dto.getRequirementIds).map(_.toSet).getOrElse(Set.empty),
      startTime = Option(dto.getStartTime),
      endTime = Option(dto.getEndTime),
      numberOfFights = Option(dto.getNumberOfFights),
      duration = Option(dto.getDuration),
      order = dto.getOrder
    )
  }

  def mapScheduleRequirement(competitionId: String)(dto: ScheduleRequirementDTO): ScheduleRequirement = {
    ScheduleRequirement(
      dto.getId,
      competitionId,
      Option(dto.getCategoryIds).map(_.toSet).getOrElse(Set.empty),
      Option(dto.getFightIds).map(_.toSet).getOrElse(Set.empty),
      Option(dto.getMatId),
      Option(dto.getPeriodId),
      Option(dto.getName),
      Option(dto.getColor),
      dto.getEntryType,
      dto.isForce,
      Option(dto.getStartTime),
      Option(dto.getEndTime),
      Option(dto.getDurationSeconds),
      Option(dto.getEntryOrder)
    )
  }

  def mapCompScore(o: CompScoreDTO): CompScore = {
    CompScore(
      Option(o.getPlaceholderId),
      Option(o.getCompetitorId),
      Option(o.getScore).orElse(Some(createEmptyScore)).map(s =>
        Score(
          s.getPoints,
          s.getAdvantages,
          s.getPenalties,
          Option(s.getPointGroups).map(_.toSet).map(_.map(pg =>
            PointGroup(pg.getId, Option(pg.getName), Option(pg.getPriority.intValue()), Option(pg.getValue.intValue()))
          )).getOrElse(Set.empty)
        )
      ).get,
      Option(o.getParentReferenceType),
      Option(o.getParentFightId)
    )
  }

  def mapCompetitor[F[+_]: Monad](dto: CompetitorDTO): F[Competitor] = Monad[F].pure {
    Competitor(
      dto.getCompetitionId,
      Option(dto.getUserId),
      dto.getEmail,
      dto.getId,
      dto.getFirstName,
      dto.getLastName,
      Option(dto.getBirthDate),
      Option(dto.getAcademy).map(a => Academy(a.getId, a.getName)),
      Option(dto.getCategories).map(_.toSet).getOrElse(Set.empty),
      dto.isPlaceholder,
      Option(dto.getPromo)
    )
  }

  def mapCategoryDescriptor[F[+_]: Monad](competitionId: String)(dto: CategoryDescriptorDTO): F[Category] = {
    Monad[F].pure(Category(
      dto.getId,
      competitionId,
      Option(dto.getRestrictions).map(_.toList).map(_.map(d => mapRestriction(d))).getOrElse(List.empty),
      Option(dto.getName),
      dto.getRegistrationOpen
    ))
  }

  def mapRestriction(d: CategoryRestrictionDTO): Restriction = {
    Restriction(
      d.getRestrictionId,
      d.getType,
      Option(d.getName),
      Option(d.getValue),
      Option(d.getAlias),
      Option(d.getMinValue),
      Option(d.getMaxValue),
      Option(d.getName),
      d.getRestrictionOrder
    )
  }

  def mapStageResultDescriptor(dto: StageResultDescriptorDTO): StageResultDescriptor = {
    StageResultDescriptor(
      dto.getName,
      dto.isForceManualAssignment,
      dto.getOutputSize,
      Option(dto.getFightResultOptions).map(_.toList).map(_.map(mapFightResultOption)).getOrElse(List.empty),
      Option(dto.getCompetitorResults).map(_.toList).map(_.map(mapCompetitorStageResult)).getOrElse(List.empty),
      Option(dto.getAdditionalGroupSortingDescriptors).map(_.toList).map(_.map(mapAdditionalGroupSortingDescriptor))
        .getOrElse(List.empty)
    )
  }

  def mapMat(dto: MatDescriptionDTO): Mat = { Mat(dto.getId, dto.getName, dto.getMatOrder) }

  def toDtoCategory(cat: Category): CategoryDescriptorDTO = {
    new CategoryDescriptorDTO().setName(cat.name.getOrElse("")).setId(cat.id).setRegistrationOpen(cat.registrationOpen)
      .setRestrictions(Option(cat.restrictions).getOrElse(List.empty).map(toDtoRestriction).toArray)
  }

  def toDtoRestriction(restr: Restriction): CategoryRestrictionDTO = {
    new CategoryRestrictionDTO().setName(restr.name.getOrElse("")).setRestrictionId(restr.restrictionId)
      .setType(restr.restrictionType).setUnit(restr.unit.getOrElse("")).setRestrictionOrder(restr.restrictionOrder)
      .setAlias(restr.alias.getOrElse("")).setMaxValue(restr.maxValue.getOrElse(""))
      .setMinValue(restr.minValue.getOrElse("")).setValue(restr.value.getOrElse(""))
  }

  def mapFight(dto: FightDescriptionDTO): Fight = {
    Fight(
      dto.getId,
      dto.getCompetitionId,
      dto.getStageId,
      dto.getCategoryId,
      ScheduleInfo(
        Option(dto.getMatId),
        Option(dto.getNumberOnMat).map(_.toInt),
        Option(dto.getPeriod),
        Option(dto.getStartTime),
        Option(dto.getInvalid),
        Option(dto.getScheduleEntryId)
      ),
      Some(
        BracketsInfo(Option(dto.getNumberInRound), Option(dto.getWinFight), Option(dto.getLoseFight), dto.getRoundType)
      ),
      Option(dto.getFightResult).map(mapFightResult),
      Option(dto.getScores).map(_.toSet).map(_.map(mapCompScore)).getOrElse(Set.empty)
    )
  }

  private def mapFightResult(d: FightResultDTO) = {
    FightResult(Option(d.getWinnerId), Option(d.getResultTypeId), Option(d.getReason))
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
    d.getNumberOfCompetitors,
    Option(d.getSelectors).map(_.toList).map(_.map(mapCompetitorSelector)).getOrElse(List.empty),
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

  def mapStageDescriptor[F[+_]: Monad](s: StageDescriptorDTO): F[StageDescriptor] = Monad[F].pure {
    StageDescriptor(
      s.getId,
      Option(s.getName),
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
      Option(s.getGroupDescriptors).map(_.toList)
        .map(_.map(dto => GroupDescriptor(dto.getId, Option(dto.getName), dto.getSize))).orElse(Option(List.empty)),
      Option(s.getNumberOfFights),
      Option(s.getFightDuration).map(_.longValue()).orElse(Option(0L))
    )
  }

  def mapPeriod(competitionId: String)(dto: PeriodDTO)(mats: Seq[Mat]): Period = {
    Period(
      competitionId,
      Option(dto.getName),
      dto.getId,
      mats.toList,
      Option(dto.getStartTime),
      Option(dto.getEndTime),
      dto.getIsActive,
      dto.getTimeBetweenFights,
      dto.getRiskPercent.intValue(),
      Option(dto.getScheduleEntries).map(_.map(mapScheduleEntry(competitionId))).map(_.toList)
        .orElse(Option(List.empty)).get,
      Option(dto.getScheduleRequirements).map(_.map(mapScheduleRequirement(competitionId))).map(_.toList)
        .orElse(Option(List.empty)).get
    )
  }

  def mapRegistrationPeriod[F[+_]: Monad](competitionId: String)(r: RegistrationPeriodDTO): F[RegistrationPeriod] =
    Monad[F].pure {
      RegistrationPeriod(
        competitionId,
        r.getId,
        Option(r.getName),
        Option(r.getStart),
        Option(r.getEnd),
        Option(r.getRegistrationGroupIds).map(_.toSet).getOrElse(Set.empty)
      )
    }
  def mapRegistrationGroup[F[+_]: Monad](competitionId: String)(r: RegistrationGroupDTO): F[RegistrationGroup] =
    Monad[F].pure {
      RegistrationGroup(
        competitionId,
        r.getId,
        r.getDefaultGroup,
        Some(RegistrationFee(
          currency = "Rub",
          r.getRegistrationFee.intValue(),
          Option(r.getRegistrationFee.remainder(BigDecimal(10)).intValue)
        )),
        categories = Option(r.getCategories).map(_.toSet).getOrElse(Set.empty)
      )
    }

  def mapCompetitionProperties[F[+_]: Monad](
    registrationOpen: Boolean
  )(r: CompetitionPropertiesDTO): F[CompetitionProperties] = Monad[F].pure {
    CompetitionProperties(
      r.getId,
      r.getCreatorId,
      Option(r.getStaffIds).map(_.toSet).orElse(Option(Set.empty)),
      r.getCompetitionName,
      CompetitionInfoTemplate(Option(r.getEmailTemplate).map(_.getBytes).getOrElse(Array.empty)),
      r.getStartDate,
      r.getSchedulePublished,
      r.getBracketsPublished,
      Option(r.getEndDate),
      r.getTimeZone,
      registrationOpen,
      r.getCreationTimestamp,
      r.getStatus
    )
  }
}
