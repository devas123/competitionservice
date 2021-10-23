package compman.compsrv.query.model.mapping

import cats.Monad
import compman.compsrv.model.dto.brackets._
import compman.compsrv.model.dto.competition._
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.{PeriodDTO, ScheduleEntryDTO, ScheduleRequirementDTO}
import compman.compsrv.model.dto.schedule
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate

import java.math.BigDecimal
import java.util.Date

object DtoMapping {
  def createEmptyScore: ScoreDTO = new ScoreDTO().setAdvantages(0).setPenalties(0).setPoints(0)
    .setPointGroups(Array.empty)

  def mapScheduleEntry(competitionId: String)(dto: ScheduleEntryDTO): ScheduleEntry = {
    ScheduleEntry(
      entryId = dto.getId,
      competitionId = competitionId,
      categoryIds = Option(dto.getCategoryIds).map(_.toSet).getOrElse(Set.empty),
      fightIds = Option(dto.getFightIds).map(_.toList).orElse(Option(List.empty))
        .map(_.map(d => MatIdAndSomeId(d.getMatId, d.getSomeId, Option(d.getStartTime).map(Date.from)))).get,
      periodId = dto.getPeriodId,
      description = Option(dto.getDescription),
      name = Option(dto.getName),
      color = Option(dto.getColor),
      entryType = dto.getEntryType,
      requirementIds = Option(dto.getRequirementIds).map(_.toSet).getOrElse(Set.empty),
      startTime = Option(dto.getStartTime).map(Date.from),
      endTime = Option(dto.getEndTime).map(Date.from),
      numberOfFights = Option(dto.getNumberOfFights),
      entryDuration = Option(dto.getDuration),
      entryOrder = dto.getOrder
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
      Option(dto.getStartTime).map(Date.from),
      Option(dto.getEndTime).map(Date.from),
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
          Option(s.getPointGroups).map(_.toList).map(_.map(pg =>
            PointGroup(pg.getId, Option(pg.getName), Option(pg.getPriority.intValue()), Option(pg.getValue.intValue()))
          )).getOrElse(List.empty)
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
      Option(dto.getPromo),
      Option(dto.getRegistrationStatus)
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

  def toDtoAcademy(a: Academy): AcademyDTO = new AcademyDTO().setName(a.academyName).setId(a.academyId)

  def toDtoCompetitor(competitor: Competitor): CompetitorDTO = {
    new CompetitorDTO().setId(competitor.id).setEmail(competitor.email).setUserId(competitor.userId.getOrElse(""))
      .setFirstName(competitor.firstName).setLastName(competitor.lastName).setBirthDate(competitor.birthDate.orNull)
      .setAcademy(competitor.academy.map(toDtoAcademy).orNull).setCategories(competitor.categories.toArray)
      .setCompetitionId(competitor.competitionId).setRegistrationStatus(competitor.registrationStatus.orNull)
      .setPlaceholder(competitor.isPlaceholder).setPromo(competitor.promo.getOrElse(""))
  }

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

  def toDtoCompetitionProperties(competitionProperties: CompetitionProperties): CompetitionPropertiesDTO = {
    new CompetitionPropertiesDTO().setId(competitionProperties.id).setCreatorId(competitionProperties.creatorId)
      .setStaffIds(competitionProperties.staffIds.getOrElse(Set.empty).toArray).setEmailNotificationsEnabled(false)
      .setCompetitionName(competitionProperties.competitionName)
      .setEmailTemplate(new String(competitionProperties.infoTemplate.template)).setPromoCodes(Array.empty)
      .setStartDate(competitionProperties.startDate.toInstant).setSchedulePublished(competitionProperties.schedulePublished)
      .setBracketsPublished(competitionProperties.bracketsPublished).setEndDate(competitionProperties.endDate.map(_.toInstant).orNull)
      .setTimeZone(competitionProperties.timeZone).setCreationTimestamp(competitionProperties.creationTimestamp.toInstant)
      .setStatus(competitionProperties.status)
  }

  def mapMat(dto: MatDescriptionDTO): Mat = { Mat(dto.getId, dto.getName, dto.getMatOrder) }

  def toDtoCompScore(cs: CompScore, order: Int): CompScoreDTO = {
    new CompScoreDTO().setOrder(order).setCompetitorId(cs.competitorId.orNull).setPlaceholderId(cs.placeholderId.orNull)
      .setParentFightId(cs.parentFightId.orNull).setParentReferenceType(cs.parentReferenceType.orNull).setScore(
        new ScoreDTO().setAdvantages(cs.score.advantages).setPoints(cs.score.points).setPenalties(cs.score.penalties)
          .setPointGroups(
            cs.score.pointGroups.map(pg =>
              new PointGroupDTO().setId(pg.id).setName(pg.name.orNull)
                .setPriority(pg.priority.map(v => new BigDecimal(v)).orNull)
                .setValue(pg.value.map(v => new BigDecimal(v)).orNull)
            ).toArray
          )
      )
  }

  def toDtoFightResult(fr: FightResult): FightResultDTO = {
    new FightResultDTO().setReason(fr.reason.orNull).setWinnerId(fr.winnerId.orNull)
      .setResultTypeId(fr.resultTypeId.orNull)
  }

  def toDtoFight(f: Fight): FightDescriptionDTO = {
    import cats.implicits._
    new FightDescriptionDTO().setId(f.id).setCategoryId(f.categoryId).setFightName(f.id)
      .setWinFight(f.bracketsInfo.flatMap(_.winFight).orNull).setLoseFight(f.bracketsInfo.flatMap(_.loseFight).orNull)
      .setScores(f.scores.mapWithIndex((c, i) => toDtoCompScore(c, i)).toArray)
      .setDuration(new BigDecimal(f.durationSeconds))
      .setRound(f.bracketsInfo.flatMap(_.round).map(Integer.valueOf).orNull)
      .setInvalid(f.invalid.map(java.lang.Boolean.valueOf).getOrElse(false))
      .setRoundType(f.bracketsInfo.map(_.roundType).orNull).setStatus(f.status.orNull)
      .setFightResult(f.fightResult.map(toDtoFightResult).orNull).setMat(
        f.matId.map(m =>
          new MatDescriptionDTO().setId(m).setName(f.matName.orNull).setPeriodId(f.periodId.orNull)
            .setMatOrder(f.matOrder.map(Integer.valueOf).orNull)
        ).orNull
      ).setNumberOnMat(f.numberOnMat.map(Integer.valueOf).orNull).setPriority(f.priority.map(Integer.valueOf).orNull)
      .setCompetitionId(f.competitionId).setPeriod(f.periodId.orNull).setStartTime(f.startTime.map(_.toInstant).orNull)
      .setStageId(f.stageId).setGroupId(f.bracketsInfo.flatMap(_.groupId).orNull)
      .setScheduleEntryId(f.scheduleEntryId.orNull)
      .setNumberInRound(f.bracketsInfo.flatMap(_.numberInRound).map(Integer.valueOf).orNull)
  }

  def mapFight(dto: FightDescriptionDTO): Fight = {
    Fight(
      dto.getId,
      dto.getCompetitionId,
      dto.getStageId,
      dto.getCategoryId,
      Option(dto.getMat).flatMap(m => Option(m.getId)),
      Option(dto.getMat).flatMap(m => Option(m.getName)),
      Option(dto.getMat).flatMap(m => Option(m.getMatOrder)),
      Option(dto.getDuration).map(_.intValue()).getOrElse(0),
      Option(dto.getStatus),
      Option(dto.getNumberOnMat).map(_.toInt),
      Option(dto.getPeriod),
      Option(dto.getStartTime).map(Date.from),
      Option(dto.getInvalid),
      Option(dto.getScheduleEntryId),
      Option(dto.getPriority),
      Some(BracketsInfo(
        Option(dto.getRound),
        Option(dto.getNumberInRound),
        Option(dto.getGroupId),
        Option(dto.getWinFight),
        Option(dto.getLoseFight),
        dto.getRoundType
      )),
      Option(dto.getFightResult).map(mapFightResult),
      Option(dto.getScores).map(_.toList).map(_.map(mapCompScore)).getOrElse(List.empty)
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
      Option(dto.getStartTime).map(Date.from),
      Option(dto.getEndTime).map(Date.from),
      dto.getIsActive,
      dto.getTimeBetweenFights,
      dto.getRiskPercent.intValue(),
      Option(dto.getScheduleEntries).map(_.map(mapScheduleEntry(competitionId))).map(_.toList)
        .orElse(Option(List.empty)).get,
      Option(dto.getScheduleRequirements).map(_.map(mapScheduleRequirement(competitionId))).map(_.toList)
        .orElse(Option(List.empty)).get
    )
  }

  def toDtoPeriod(o: Period): PeriodDTO = {
    new PeriodDTO().setId(o.id).setName(o.name.orNull).setStartTime(o.startTime.map(_.toInstant).orNull)
      .setEndTime(o.endTime.map(_.toInstant).orNull)
      .setScheduleEntries(o.scheduleEntries.map(toDtopScheduleEntry(o.id)).toArray)
      .setScheduleRequirements(o.scheduleRequirements.map(toDtopScheduleRequirement).toArray).setIsActive(o.active)
      .setRiskPercent(BigDecimal.valueOf(o.riskCoefficient)).setTimeBetweenFights(o.timeBetweenFights)
  }

  def toDtopScheduleEntry(periodId: String)(o: ScheduleEntry): ScheduleEntryDTO = {
    new ScheduleEntryDTO().setId(o.entryId).setDescription(o.description.orNull).setEntryType(o.entryType)
      .setName(o.name.orNull).setPeriodId(periodId).setOrder(o.entryOrder).setEndTime(o.endTime.map(_.toInstant).orNull)
      .setStartTime(o.startTime.map(_.toInstant).orNull).setColor(o.color.orNull)
      .setDuration(o.entryDuration.getOrElse(0)).setFightIds(o.fightIds.map(toDtoMatIdAndSomeId).toArray)
      .setNumberOfFights(o.numberOfFights.getOrElse(0).intValue()).setCategoryIds(o.categoryIds.toArray)
      .setRequirementIds(o.requirementIds.toArray)
  }

  def toDtoMatIdAndSomeId(m: MatIdAndSomeId): schedule.MatIdAndSomeId = {
    new schedule.MatIdAndSomeId(m.matId, m.startTime.map(_.toInstant).orNull, m.someId)
  }

  def toDtopScheduleRequirement(o: ScheduleRequirement): ScheduleRequirementDTO = {
    new ScheduleRequirementDTO().setId(o.entryId).setCategoryIds(o.categoryIds.toArray).setFightIds(o.fightIds.toArray)
      .setMatId(o.matId.orNull).setPeriodId(o.periodId.get).setName(o.name.orNull).setColor(o.color.orNull)
      .setEntryType(o.entryType).setForce(o.force).setStartTime(o.startTime.map(_.toInstant).orNull)
      .setEndTime(o.endTime.map(_.toInstant).orNull).setDurationSeconds(o.durationSeconds.getOrElse(0))
      .setEntryOrder(o.entryOrder.getOrElse(0).intValue())

  }

  def toDtoMat(periodId: String)(o: Mat): MatDescriptionDTO = new MatDescriptionDTO().setId(o.matId).setName(o.name)
    .setMatOrder(o.matOrder).setPeriodId(periodId)

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
          Option(r.getRegistrationFee.remainder(new BigDecimal(10)).intValue)
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
      Date.from(r.getStartDate),
      r.getSchedulePublished,
      r.getBracketsPublished,
      Option(r.getEndDate).map(Date.from),
      r.getTimeZone,
      registrationOpen,
      Date.from(r.getCreationTimestamp),
      r.getStatus
    )
  }
}
