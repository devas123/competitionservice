package compman.compsrv.query.model.mapping

import cats.Monad
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.timestamp.Timestamp.toJavaProto
import com.google.protobuf.util.Timestamps
import compman.compsrv.model.extensions.InstantOps
import compman.compsrv.query.model._
import compman.compsrv.query.model.CompetitionProperties.CompetitionInfoTemplate
import compman.compsrv.query.model.academy.FullAcademyInfo
import compservice.model.protobuf.model

import java.time.Instant
import java.util.Date

object DtoMapping {
  def mapRegistrationInfo(competitionId: String)(dto: model.RegistrationInfo): RegistrationInfo = RegistrationInfo(
    id = dto.id,
    registrationGroups = dto.registrationGroups.view.mapValues(mapRegistrationGroup(competitionId)).toMap,
    registrationPeriods = dto.registrationPeriods.view.mapValues(mapRegistrationPeriod(competitionId)).toMap,
    registrationOpen = dto.registrationOpen
  )
  def toDtoRegistrationInfo(o: RegistrationInfo): model.RegistrationInfo = model.RegistrationInfo(
    id = o.id,
    registrationGroups = o.registrationGroups.view.mapValues(toDtoRegistrationGroup).toMap,
    registrationPeriods = o.registrationPeriods.view.mapValues(toDtoRegistrationPeriod).toMap,
    registrationOpen = o.registrationOpen
  )

  def toDtoManagedCompetition(o: ManagedCompetition): model.ManagedCompetition = {
    model.ManagedCompetition().update(
      _.id := o.id,
      _.competitionName.setIfDefined(o.competitionName),
      _.eventsTopic := o.eventsTopic,
      _.creatorId.setIfDefined(o.creatorId),
      _.createdAt := o.creationTimestamp.asTimestamp,
      _.startsAt  := o.startDate.asTimestamp,
      _.endsAt.setIfDefined(o.endDate.map(_.asTimestamp)),
      _.timeZone := o.timeZone,
      _.status   := o.status
    )
  }

  def toDtoAdditionalGroupSortingDescriptor(
    o: AdditionalGroupSortingDescriptor
  ): model.AdditionalGroupSortingDescriptor = {
    model.AdditionalGroupSortingDescriptor().withGroupSortDirection(o.groupSortDirection)
      .withGroupSortSpecifier(o.groupSortSpecifier)
  }

  def toDtoCompetitorResult(o: CompetitorStageResult): model.CompetitorStageResult = {
    model.CompetitorStageResult().withCompetitorId(o.competitorId).withPoints(o.points).withRound(o.round)
      .withRoundType(o.roundType).withPlace(o.place).withStageId(o.stageId).withGroupId(o.groupId.orNull)
      .withConflicting(o.conflicting)
  }

  def createEmptyScore: model.Score = model.Score().withAdvantages(0).withPenalties(0).withPoints(0)
    .withPointGroups(Seq.empty)

  def toDtoFightResultOption(fightResultOption: FightResultOption): model.FightResultOption = {
    model.FightResultOption().withId(fightResultOption.optionId).withDescription(fightResultOption.description.orNull)
      .withShortName(fightResultOption.shortName.orNull).withDraw(fightResultOption.draw)
      .withWinnerPoints(fightResultOption.winnerPoints)
      .withWinnerAdditionalPoints(fightResultOption.winnerAdditionalPoints)
      .withLoserPoints(fightResultOption.loserPoints).withLoserAdditionalPoints(fightResultOption.loserAdditionalPoints)
  }

  def toDtoStageResultDescriptor(o: StageResultDescriptor): model.StageResultDescriptor = {
    model.StageResultDescriptor().withName(o.name.orNull).withForceManualAssignment(o.forceManualAssignment)
      .withOutputSize(o.outputSize).withFightResultOptions(o.fightResultOptions.map(DtoMapping.toDtoFightResultOption))
      .withCompetitorResults(o.competitorResults.map(DtoMapping.toDtoCompetitorResult))
      .withAdditionalGroupSortingDescriptors(o.additionalGroupSortingDescriptors.map(
        DtoMapping.toDtoAdditionalGroupSortingDescriptor
      ))
  }

  def toDtoCompetitorSelector(o: CompetitorSelector): model.CompetitorSelector = {
    model.CompetitorSelector().withApplyToStageId(o.applyToStageId).withLogicalOperator(o.logicalOperator)
      .withClassifier(o.classifier).withOperator(o.operator).withSelectorValue(o.selectorValue.toSeq)
  }

  def toDtoStageInputDescriptor(o: StageInputDescriptor): model.StageInputDescriptor = {
    model.StageInputDescriptor().withNumberOfCompetitors(o.numberOfCompetitors)
      .withSelectors(o.selectors.map(toDtoCompetitorSelector)).withDistributionType(o.distributionType)
  }

  def toDtoGroupDescriptor(o: GroupDescriptor): model.GroupDescriptor = {
    model.GroupDescriptor().withId(o.groupId).withName(o.name.orNull).withSize(o.size)
  }

  val toDate: Timestamp => Date       = (ts: Timestamp) => new Date(Timestamps.toMillis(toJavaProto(ts)))
  val toInstant: Timestamp => Instant = (ts: Timestamp) => Instant.ofEpochMilli(Timestamps.toMillis(toJavaProto(ts)))

  def toDtoStageDescriptor(stageDescriptor: StageDescriptor): model.StageDescriptor = {
    model.StageDescriptor().withId(stageDescriptor.id).withName(stageDescriptor.name.orNull)
      .withCategoryId(stageDescriptor.categoryId).withCompetitionId(stageDescriptor.competitionId)
      .withBracketType(stageDescriptor.bracketType).withStageType(stageDescriptor.stageType)
      .withStageStatus(stageDescriptor.stageStatus)
      .withStageResultDescriptor(stageDescriptor.stageResultDescriptor.map(toDtoStageResultDescriptor).orNull)
      .withInputDescriptor(stageDescriptor.inputDescriptor.map(toDtoStageInputDescriptor).orNull)
      .withStageOrder(stageDescriptor.stageOrder).withWaitForPrevious(stageDescriptor.waitForPrevious)
      .withHasThirdPlaceFight(stageDescriptor.hasThirdPlaceFight)
      .withGroupDescriptors(stageDescriptor.groupDescriptors.map(_.map(toDtoGroupDescriptor)).getOrElse(Seq.empty))
      .withNumberOfFights(stageDescriptor.numberOfFights.orElse(Option(0)).get)
      .withFightDuration(stageDescriptor.fightDuration.orElse(Option(0)).get)
  }

  def mapScheduleEntry(competitionId: String)(dto: model.ScheduleEntry): ScheduleEntry = {
    ScheduleEntry(
      entryId = dto.id,
      competitionId = competitionId,
      categoryIds = Option(dto.categoryIds).map(_.toSet).getOrElse(Set.empty),
      fightIds = Option(dto.fightScheduleInfo).map(_.toList).orElse(Option(List.empty)).map(_.map(d =>
        MatIdAndSomeId(d.matId, d.someId, d.startTime.map(ts => new Date(Timestamps.toMillis(toJavaProto(ts)))))
      )).get,
      periodId = dto.periodId,
      description = dto.description,
      name = dto.name,
      color = dto.color,
      entryType = dto.entryType,
      requirementIds = Option(dto.requirementIds).map(_.toSet).getOrElse(Set.empty),
      startTime = dto.startTime.map(toDate),
      endTime = dto.endTime.map(toDate),
      numberOfFights = Option(dto.numberOfFights),
      entryDuration = Option(dto.duration),
      entryOrder = dto.order
    )
  }

  def mapScheduleRequirement(competitionId: String)(dto: model.ScheduleRequirement): ScheduleRequirement = {
    ScheduleRequirement(
      dto.id,
      competitionId,
      Option(dto.categoryIds).map(_.toSet).getOrElse(Set.empty),
      Option(dto.fightIds).map(_.toSet).getOrElse(Set.empty),
      dto.matId,
      Option(dto.periodId),
      dto.name,
      dto.color,
      dto.entryType,
      dto.force,
      dto.startTime.map(toDate),
      dto.endTime.map(toDate),
      dto.durationSeconds,
      Option(dto.entryOrder)
    )
  }

  def mapCompScore(o: model.CompScore, cd: Option[CompetitorDisplayInfo]): CompScore = {
    CompScore(
      Option(o.getPlaceholderId),
      o.competitorId,
      cd.flatMap(_.competitorFirstName),
      cd.flatMap(_.competitorLastName),
      cd.flatMap(_.competitorAcademyName),
      Option(o.getScore).orElse(Some(createEmptyScore)).map(s =>
        Score(
          s.points,
          s.advantages,
          s.penalties,
          Option(s.pointGroups).map(_.toList).map(_.map(pg =>
            PointGroup(pg.id, Option(pg.name), Option(pg.priority.intValue()), Option(pg.value.intValue()))
          )).getOrElse(List.empty)
        )
      ).get,
      Option(o.getParentReferenceType),
      Option(o.getParentFightId)
    )
  }

  def mapCompetitor(dto: model.Competitor): Competitor =     Competitor(
    dto.competitionId,
    Option(dto.userId),
    dto.email,
    dto.id,
    dto.firstName,
    dto.lastName,
    dto.birthDate.map(toInstant),
    dto.academy.map(a => Academy(a.id, a.name)),
    Option(dto.categories).map(_.toSet).getOrElse(Set.empty),
    dto.placeholder,
    Option(dto.promo),
    Option(dto.registrationStatus)
  )

  def mapCategoryDescriptor[F[+_]: Monad](competitionId: String)(dto: model.CategoryDescriptor): F[Category] = {
    Monad[F].pure(Category(
      dto.id,
      competitionId,
      Option(dto.restrictions).map(_.toList).map(_.map(d => mapRestriction(d))).getOrElse(List.empty),
      dto.name,
      dto.registrationOpen
    ))
  }

  def mapAcademy(dto: model.FullAcademyInfo): FullAcademyInfo = {
    FullAcademyInfo(dto.id, Option(dto.name), Option(dto.coaches).map(_.toSet))
  }

  def mapRestriction(d: model.CategoryRestriction): Restriction = {
    Restriction(
      d.restrictionId,
      d.`type`,
      Option(d.name),
      d.value,
      Option(d.getAlias),
      Option(d.getMinValue),
      Option(d.getMaxValue),
      Option(d.name),
      d.restrictionOrder
    )
  }

  def mapStageResultDescriptor(dto: model.StageResultDescriptor): StageResultDescriptor = {
    StageResultDescriptor(
      dto.name,
      dto.forceManualAssignment,
      dto.outputSize.getOrElse(0),
      Option(dto.fightResultOptions).map(_.toList).map(_.map(mapFightResultOption)).getOrElse(List.empty),
      Option(dto.competitorResults).map(_.toList).map(_.map(mapCompetitorStageResult)).getOrElse(List.empty),
      Option(dto.additionalGroupSortingDescriptors).map(_.toList).map(_.map(mapAdditionalGroupSortingDescriptor))
        .getOrElse(List.empty)
    )
  }

  def toDtoAcademy(a: Academy): model.Academy = model.Academy().withName(a.academyName).withId(a.academyId)

  def toDtoCompetitor(competitor: Competitor): model.Competitor = {
    model.Competitor().withId(competitor.id).withEmail(competitor.email).withUserId(competitor.userId.getOrElse(""))
      .withFirstName(competitor.firstName).withLastName(competitor.lastName)
      .withAcademy(competitor.academy.map(toDtoAcademy).orNull).withCategories(competitor.categories.toSeq)
      .withCompetitionId(competitor.competitionId).withRegistrationStatus(competitor.registrationStatus.orNull)
      .withPlaceholder(competitor.isPlaceholder).withPromo(competitor.promo.getOrElse(""))
      .update(_.birthDate.setIfDefined(competitor.birthDate.map(_.asTimestamp)))
  }
  def toDtoCompetitor(competitorDisplayInfo: CompetitorDisplayInfo): model.Competitor = {
    model.Competitor().withId(competitorDisplayInfo.competitorId)
      .withFirstName(competitorDisplayInfo.competitorFirstName.orNull)
      .withLastName(competitorDisplayInfo.competitorLastName.orNull)
      .withAcademy(model.Academy().withName(competitorDisplayInfo.competitorAcademyName.orNull))
  }

  def toDtoCategory(cat: Category): model.CategoryDescriptor = {
    model.CategoryDescriptor().withName(cat.name.getOrElse("")).withId(cat.id)
      .withRegistrationOpen(cat.registrationOpen)
      .withRestrictions(Option(cat.restrictions).getOrElse(List.empty).map(toDtoRestriction))
  }

  def toDtoRestriction(restr: Restriction): model.CategoryRestriction = {
    model.CategoryRestriction().withName(restr.name.getOrElse("")).withRestrictionId(restr.restrictionId)
      .withType(restr.restrictionType).withUnit(restr.unit.getOrElse("")).withRestrictionOrder(restr.restrictionOrder)
      .withAlias(restr.alias.getOrElse("")).withMaxValue(restr.maxValue.getOrElse(""))
      .withMinValue(restr.minValue.getOrElse("")).withValue(restr.value.getOrElse(""))
  }

  def toDtoCompetitionProperties(competitionProperties: CompetitionProperties): model.CompetitionProperties = {
    model.CompetitionProperties().withId(competitionProperties.id).withCreatorId(competitionProperties.creatorId)
      .withStaffIds(competitionProperties.staffIds.getOrElse(Set.empty).toSeq).withEmailNotificationsEnabled(false)
      .withCompetitionName(competitionProperties.competitionName)
      .withEmailTemplate(new String(competitionProperties.infoTemplate.template)).withPromoCodes(Seq.empty)
      .withStartDate(competitionProperties.startDate.toInstant.asTimestamp)
      .withSchedulePublished(competitionProperties.schedulePublished)
      .withBracketsPublished(competitionProperties.bracketsPublished).withTimeZone(competitionProperties.timeZone)
      .withCreationTimestamp(competitionProperties.creationTimestamp.toInstant.asTimestamp)
      .withStatus(competitionProperties.status)
      .update(_.endDate.setIfDefined(competitionProperties.endDate.map(_.toInstant.asTimestamp)))
  }

  def mapMat(dto: model.MatDescription): Mat = { Mat(dto.id, dto.name, dto.matOrder) }

  def toDtoCompScore(cs: CompScore, order: Int): model.CompScore = {
    model.CompScore().withOrder(order).withCompetitorId(cs.competitorId.orNull)
      .withPlaceholderId(cs.placeholderId.orNull).withParentFightId(cs.parentFightId.orNull)
      .withParentReferenceType(cs.parentReferenceType.orNull).withScore(
        model.Score().withAdvantages(cs.score.advantages).withPoints(cs.score.points).withPenalties(cs.score.penalties)
          .withPointGroups(cs.score.pointGroups.map(pg =>
            model.PointGroup().withId(pg.id).withName(pg.name.orNull).withPriority(pg.priority.orElse(Option(0)).get)
              .withValue(pg.value.orElse(Option(0)).get)
          ))
      )
  }

  def toDtoFightResult(fr: FightResult): model.FightResult = {
    model.FightResult().withReason(fr.reason.orNull).withWinnerId(fr.winnerId.orNull)
      .withResultTypeId(fr.resultTypeId.orNull)
  }

  def toDtoFight(f: Fight): model.FightDescription = {
    import cats.implicits._
    model.FightDescription().withId(f.id).withCategoryId(f.categoryId)
      .withScores(f.scores.mapWithIndex((c, i) => toDtoCompScore(c, i))).withDuration(f.durationSeconds)
      .withInvalid(f.invalid.getOrElse(false)).withCompetitionId(f.competitionId).withStageId(f.stageId).update(
        _.connections.setIfDefined(f.bracketsInfo.map(_.connections.map(toDtoFightRefence))),
        _.fightName.setIfDefined(f.fightName),
        _.numberOnMat.setIfDefined(f.numberOnMat),
        _.numberInRound.setIfDefined(f.bracketsInfo.flatMap(_.numberInRound)),
        _.scheduleEntryId.setIfDefined(f.scheduleEntryId),
        _.groupId.setIfDefined(f.bracketsInfo.flatMap(_.groupId)),
        _.startTime.setIfDefined(f.startTime.map(_.toInstant.asTimestamp)),
        _.period.setIfDefined(f.periodId),
        _.priority.setIfDefined(f.priority),
        _.round.setIfDefined(f.bracketsInfo.flatMap(_.round)),
        _.roundType.setIfDefined(f.bracketsInfo.map(_.roundType)),
        _.status.setIfDefined(f.status),
        _.fightResult.setIfDefined(f.fightResult.map(toDtoFightResult)),
        _.mat.setIfDefined(f.matId.map(m =>
          model.MatDescription().withId(m).update(
            _.name.setIfDefined(f.matName),
            _.periodId.setIfDefined(f.periodId),
            _.matOrder.setIfDefined(f.matOrder)
          )
        ))
      )
  }

  def mapFight(coms: Map[String, CompetitorDisplayInfo])(dto: model.FightDescription): Fight = {
    Fight(
      dto.id,
      dto.fightName,
      dto.competitionId,
      dto.stageId,
      dto.categoryId,
      dto.mat.flatMap(m => Option(m.id)),
      dto.mat.flatMap(m => Option(m.name)),
      dto.mat.flatMap(m => Option(m.matOrder)),
      Option(dto.duration).getOrElse(0),
      Option(dto.status),
      dto.numberOnMat,
      dto.period,
      dto.startTime.map(toDate),
      Option(dto.invalid),
      dto.scheduleEntryId,
      dto.priority,
      Some(BracketsInfo(
        Option(dto.round),
        Option(dto.numberInRound),
        dto.groupId,
        dto.connections.map(mapFightRefence).toList,
        dto.roundType
      )),
      dto.fightResult.map(mapFightResult),
      Option(dto.scores).map(_.toList).map(_.map(cs => mapCompScore(cs, cs.competitorId.flatMap(coms.get))))
        .getOrElse(List.empty)
    )
  }

  def mapFightRefence(dto: model.FightReference): FightReference = { FightReference(dto.referenceType, dto.fightId) }

  def toDtoFightRefence(o: FightReference): model.FightReference = { model.FightReference(o.fightId, o.referenceType) }

  def mapFightResult(d: model.FightResult): FightResult = {
    FightResult(Option(d.getWinnerId), Option(d.getResultTypeId), Option(d.getReason))
  }

  def mapFightResultOption(dto: model.FightResultOption): FightResultOption = FightResultOption(
    dto.id,
    description = dto.description,
    shortName = Option(dto.shortName),
    draw = dto.draw,
    winnerPoints = dto.winnerPoints,
    winnerAdditionalPoints = dto.winnerAdditionalPoints.getOrElse(0),
    loserPoints = dto.loserPoints,
    loserAdditionalPoints = dto.loserAdditionalPoints.getOrElse(0)
  )

  def mapAdditionalGroupSortingDescriptor(
    dto: model.AdditionalGroupSortingDescriptor
  ): AdditionalGroupSortingDescriptor = AdditionalGroupSortingDescriptor(dto.groupSortDirection, dto.groupSortSpecifier)

  def mapCompetitorStageResult(dto: model.CompetitorStageResult): CompetitorStageResult = CompetitorStageResult(
    dto.competitorId,
    dto.points.getOrElse(0),
    dto.getRound,
    dto.roundType,
    dto.getPlace,
    dto.stageId,
    dto.groupId,
    dto.conflicting
  )

  def mapStageInputDescriptor(d: model.StageInputDescriptor): StageInputDescriptor = StageInputDescriptor(
    d.numberOfCompetitors,
    Option(d.selectors).map(_.toList).map(_.map(mapCompetitorSelector)).getOrElse(List.empty),
    d.distributionType
  )

  def mapCompetitorSelector(dto: model.CompetitorSelector): CompetitorSelector = {
    CompetitorSelector(dto.applyToStageId, dto.logicalOperator, dto.classifier, dto.operator, dto.selectorValue.toSet)
  }

  def mapStageDescriptor[F[+_]: Monad](s: model.StageDescriptor): F[StageDescriptor] = Monad[F].pure {
    StageDescriptor(
      s.id,
      s.name,
      s.categoryId,
      s.competitionId,
      s.bracketType,
      s.stageType,
      s.stageStatus,
      Option(s.getStageResultDescriptor).map(mapStageResultDescriptor),
      Option(s.getInputDescriptor).map(mapStageInputDescriptor),
      s.stageOrder,
      s.waitForPrevious,
      s.hasThirdPlaceFight,
      Option(s.groupDescriptors).map(_.toList).map(_.map(dto => GroupDescriptor(dto.id, Option(dto.name), dto.size)))
        .orElse(Option(List.empty)),
      Option(s.numberOfFights),
      Option(s.fightDuration).orElse(Option(0))
    )
  }

  def mapPeriod(competitionId: String)(dto: model.Period)(mats: Seq[Mat]): Period = {
    Period(
      competitionId,
      Option(dto.name),
      dto.id,
      mats.toList,
      dto.startTime.map(toDate),
      dto.endTime.map(toDate),
      dto.isActive,
      dto.timeBetweenFights,
      dto.riskPercent.intValue(),
      Option(dto.scheduleEntries).map(_.map(mapScheduleEntry(competitionId))).map(_.toList).orElse(Option(List.empty))
        .get,
      Option(dto.scheduleRequirements).map(_.map(mapScheduleRequirement(competitionId))).map(_.toList)
        .orElse(Option(List.empty)).get
    )
  }

  def toDtoPeriod(o: Period): model.Period = {
    model.Period().withId(o.id).withScheduleEntries(o.scheduleEntries.map(toDtopScheduleEntry(o.id)))
      .withScheduleRequirements(o.scheduleRequirements.map(toDtopScheduleRequirement)).withIsActive(o.active)
      .withRiskPercent(o.riskCoefficient).withTimeBetweenFights(o.timeBetweenFights).update(
        _.name.setIfDefined(o.name),
        _.startTime.setIfDefined(o.startTime.map(_.toInstant.asTimestamp)),
        _.endTime.setIfDefined(o.endTime.map(_.toInstant.asTimestamp))
      )
  }

  def toDtopScheduleEntry(periodId: String)(o: ScheduleEntry): model.ScheduleEntry = {
    model.ScheduleEntry().withId(o.entryId).withEntryType(o.entryType).withPeriodId(periodId).withOrder(o.entryOrder)
      .withColor(o.color.orNull).withDuration(o.entryDuration.getOrElse(0))
      .withFightScheduleInfo(o.fightIds.map(toDtoMatIdAndSomeId))
      .withNumberOfFights(o.numberOfFights.getOrElse(0).intValue()).withCategoryIds(o.categoryIds.toSeq)
      .withRequirementIds(o.requirementIds.toSeq).update(
        _.startTime.setIfDefined(o.startTime.map(_.toInstant.asTimestamp)),
        _.endTime.setIfDefined(o.endTime.map(_.toInstant.asTimestamp)),
        _.name.setIfDefined(o.name),
        _.description.setIfDefined(o.description)
      )

  }

  def toDtoMatIdAndSomeId(m: MatIdAndSomeId): model.StartTimeInfo = {
    model.StartTimeInfo(m.matId, m.startTime.map(_.toInstant.asTimestamp), m.someId)
  }

  def toDtopScheduleRequirement(o: ScheduleRequirement): model.ScheduleRequirement = {
    model.ScheduleRequirement().withId(o.entryId).withCategoryIds(o.categoryIds.toSeq).withFightIds(o.fightIds.toSeq)
      .withPeriodId(o.periodId.get).withEntryType(o.entryType).withForce(o.force)
      .withDurationSeconds(o.durationSeconds.getOrElse(0)).withEntryOrder(o.entryOrder.getOrElse(0)).update(
        _.matId.setIfDefined(o.matId),
        _.name.setIfDefined(o.name),
        _.color.setIfDefined(o.color),
        _.startTime.setIfDefined(o.startTime.map(_.toInstant.asTimestamp)),
        _.endTime.setIfDefined(o.endTime.map(_.toInstant.asTimestamp))
      )
  }

  def toDtoMat(periodId: String)(o: Mat): model.MatDescription = model.MatDescription().withId(o.matId).withName(o.name)
    .withMatOrder(o.matOrder).withPeriodId(periodId)

  def toDtoFullAcademyInfo(o: FullAcademyInfo): model.FullAcademyInfo = model.FullAcademyInfo().withId(o.id)
    .update(_.name.setIfDefined(o.name), _.coaches.setIfDefined(o.coaches.map(_.toSeq)))

  def mapRegistrationPeriod(competitionId: String)(r: model.RegistrationPeriod): RegistrationPeriod =
    RegistrationPeriod(
      competitionId,
      r.id,
      Option(r.name),
      r.start.map(toInstant),
      r.end.map(toInstant),
      Option(r.registrationGroupIds).map(_.toSet).getOrElse(Set.empty)
    )

  def mapRegistrationGroup(competitionId: String)(r: model.RegistrationGroup): RegistrationGroup = RegistrationGroup(
    competitionId,
    r.id,
    Option(r.displayName),
    r.defaultGroup,
    Some(RegistrationFee(
      currency = r.getRegistrationFee.currency,
      r.getRegistrationFee.amount,
      Option(r.getRegistrationFee.remainder)
    )),
    categories = Option(r.categories).map(_.toSet).getOrElse(Set.empty)
  )

  def toDtoRegistrationInfo(registrationOpen: Boolean, competitionId: String)(
    r: RegistrationInfo
  ): model.RegistrationInfo = {
    model.RegistrationInfo().withId(competitionId).withRegistrationOpen(registrationOpen)
      .withRegistrationGroups(r.registrationGroups.map { case (key, group) => key -> toDtoRegistrationGroup(group) })
      .withRegistrationPeriods(r.registrationPeriods.map { case (key, period) =>
        key -> toDtoRegistrationPeriod(period)
      })
  }

  def toDtoRegistrationGroup(r: RegistrationGroup): model.RegistrationGroup = {
    model.RegistrationGroup().withId(r.id).withDefaultGroup(r.isDefaultGroup)
      .withRegistrationFee(r.registrationFee.map(toDtoRegistrationFee).getOrElse(model.RegistrationFee()))
      .withDisplayName(r.displayName.getOrElse("")).withCategories(r.categories.toSeq)
  }
  def toDtoRegistrationPeriod(r: RegistrationPeriod): model.RegistrationPeriod = {
    model.RegistrationPeriod().withId(r.id).withName(r.name.getOrElse("")).withCompetitionId(r.competitionId)
      .withRegistrationGroupIds(r.registrationGroupIds.toSeq)
      .update(_.end.setIfDefined(r.end.map(_.asTimestamp)), _.start.setIfDefined(r.start.map(_.asTimestamp)))
  }

  def toDtoRegistrationFee(r: RegistrationFee): model.RegistrationFee = {
    model.RegistrationFee().withAmount(r.amount).withCurrency(r.currency).withRemainder(r.remainder.getOrElse(0))
  }

  def mapCompetitionProperties[F[+_]: Monad](r: model.CompetitionProperties): F[CompetitionProperties] = Monad[F].pure {
    CompetitionProperties(
      r.id,
      r.creatorId,
      Option(r.staffIds).map(_.toSet).orElse(Option(Set.empty)),
      r.competitionName,
      CompetitionInfoTemplate(r.emailTemplate.map(_.getBytes).getOrElse(Array.empty)),
      toDate(r.getStartDate),
      r.schedulePublished,
      r.bracketsPublished,
      Option(r.getEndDate).map(toDate),
      r.timeZone,
      toDate(r.getCreationTimestamp),
      r.status
    )
  }
}
