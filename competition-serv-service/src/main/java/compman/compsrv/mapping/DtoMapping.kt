package compman.compsrv.mapping

import com.compmanager.compservice.jooq.tables.pojos.*
import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.util.IDGenerator

fun PeriodProperties.toDTO(categories: Array<CategoryDescriptorDTO>): PeriodPropertiesDTO = PeriodPropertiesDTO()
        .setId(id)
        .setName(name)
        .setStartTime(startTime?.toInstant())
        .setNumberOfMats(numberOfMats)
        .setTimeBetweenFights(timeBetweenFights)
        .setRiskPercent(riskPercent)
        .setCategories(categories)


fun ScheduleEntries.toDTO(): ScheduleEntryDTO = ScheduleEntryDTO()
        .setCategoryId(categoryId)
        .setStartTime(startTime?.toInstant())
        .setNumberOfFights(numberOfFights)
        .setFightDuration(fightDuration)

fun Period.toDTO(scheduleEntries: Array<ScheduleEntryDTO>, fightsByMats: Array<MatScheduleContainerDTO>): PeriodDTO = PeriodDTO()
        .setId(id)
        .setName(name)
        .setSchedule(scheduleEntries)
        .setStartTime(startTime?.toInstant())
        .setNumberOfMats(numberOfMats)
        .setFightsByMats(fightsByMats)


fun MatScheduleContainer.toDTO(fights: Array<FightStartTimePairDTO>): MatScheduleContainerDTO = MatScheduleContainerDTO()
        .setTotalFights(totalFights)
        .setId(id)
        .setFights(fights)

fun FightStartTimes.toDTO(matId: String?): FightStartTimePairDTO = FightStartTimePairDTO()
        .setFightId(fightId)
        .setFightNumber(fightNumber)
        .setStartTime(startTime?.toInstant())
        .setMatId(matId)


fun CategoryRestriction.toDTO(): CategoryRestrictionDTO = CategoryRestrictionDTO().setMaxValue(maxValue).setMinValue(minValue).setName(name).setType(CategoryRestrictionType.values()[type])
        .setUnit(unit).apply {
            id = IDGenerator.restrictionId(this)
        }

fun RegistrationInfo.toDTO(registrationPeriods: Array<RegistrationPeriodDTO>, registrationGroups: Array<RegistrationGroupDTO>): RegistrationInfoDTO = RegistrationInfoDTO()
        .setId(id)
        .setRegistrationOpen(registrationOpen)
        .setRegistrationPeriods(registrationPeriods)
        .setRegistrationGroups(registrationGroups)

fun RegistrationGroup.toDTO(getCategories: (groupId: String) -> Array<String>, getPeriods: (groupId: String) -> Array<String>): RegistrationGroupDTO = RegistrationGroupDTO()
        .setRegistrationPeriodIds(getPeriods(id))
        .setDisplayName(displayName)
        .setId(id)
        .setRegistrationFee(registrationFee)
        .setCategories(getCategories(id))

fun RegistrationPeriod.toDTO(getGroups: (periodId: String) -> Array<String>): RegistrationPeriodDTO = RegistrationPeriodDTO()
        .setId(id)
        .setCompetitionId(registrationInfoId)
        .setName(name)
        .setEnd(endDate.toInstant())
        .setStart(startDate.toInstant())
        .setRegistrationGroupIds(getGroups(id))

fun PromoCode.toDTO(): PromoCodeDTO = PromoCodeDTO()
        .setId(id.toString())
        .setCoefficient(coefficient)
        .setCompetitionId(competitionId)
        .setStartAt(startAt.toInstant())
        .setExpireAt(expireAt.toInstant())

fun CompScore.toDTO(getCompetitor: (id: String) -> CompetitorDTO): CompScoreDTO = CompScoreDTO().setScore(ScoreDTO()
        .setPoints(points)
        .setAdvantages(advantages)
        .setPenalties(penalties))
        .setCompetitor(getCompetitor(compscoreCompetitorId)).setId(id)

fun CompetitionProperties.toDTO(status: Int, staffIds: Array<String>?, promoCodes: Array<PromoCodeDTO>?, getRegistrationInfo: (id: String) -> RegistrationInfoDTO?): CompetitionPropertiesDTO =
        CompetitionPropertiesDTO()
                .setId(id)
                .setBracketsPublished(bracketsPublished)
                .setSchedulePublished(schedulePublished)
                .setCreatorId(creatorId)
                .setCompetitionName(competitionName)
                .setEmailNotificationsEnabled(emailNotificationsEnabled)
                .setEmailTemplate(emailTemplate)
                .setEndDate(endDate?.toInstant())
                .setStartDate(startDate?.toInstant())
                .setStaffIds(staffIds ?: emptyArray())
                .setPromoCodes(promoCodes ?: emptyArray())
                .setTimeZone(timeZone)
                .setRegistrationInfo(getRegistrationInfo(id))
                .setCreationTimestamp(creationTimestamp)
                .setStatus(CompetitionStatus.values()[status])

fun DashboardPeriod.toDTO(mats: Array<MatDescriptionDTO>): DashboardPeriodDTO = DashboardPeriodDTO()
        .setId(id)
        .setName(name)
        .setMats(mats)
        .setStartTime(startTime?.toInstant())
        .setIsActive(isActive)

fun MatDescription.toDTO(): MatDescriptionDTO = MatDescriptionDTO().setId(id).setName(name).setDashboardPeriodId(dashboardPeriodId)

fun Competitor.toDTO(categories: Array<String>): CompetitorDTO = CompetitorDTO()
        .setId(id)
        .setAcademy(AcademyDTO(academyId, academyName))
        .setBirthDate(birthDate?.toInstant())
        .setCategories(categories)
        .setCompetitionId(competitionId)
        .setEmail(email)
        .setFirstName(firstName)
        .setLastName(lastName)
        .setPromo(promo)
        .setRegistrationStatus(registrationStatus?.let { RegistrationStatus.values()[it].name })
        .setUserId(userId)

private fun emptyBracketsDescriptor(id: String, competitionId: String) = BracketDescriptorDTO().setId(id).setCompetitionId(competitionId).setStages(emptyArray())

fun CategoryDescriptor.toDTO(competitors: Array<String>, restrictions: Array<CategoryRestrictionDTO>): CategoryDescriptorDTO = CategoryDescriptorDTO()
        .setId(id)
        .setFightDuration(fightDuration)
        .setCompetitors(competitors)
        .setRestrictions(restrictions)
        .setName(name).setRegistrationOpen(registrationOpen)

fun StageInputDescriptor.toDTO(selectors: Array<CompetitorSelectorDTO>): StageInputDescriptorDTO = StageInputDescriptorDTO()
        .setId(id)
        .setDistributionType(DistributionType.values()[distributionType])
        .setNumberOfCompetitors(numberOfCompetitors).setSelectors(selectors)

fun StageResultDescriptor.toDTO(competitorResults: Array<CompetitorResultDTO>): StageResultDescriptorDTO =
        StageResultDescriptorDTO().setId(id).setName(name).setCompetitorResults(competitorResults)

fun CompetitorResult.toDTO(): CompetitorResultDTO = CompetitorResultDTO()
        .setId(id)
        .setPoints(points)
        .setGroupId(groupId)
        .setPlace(place)
        .setRound(round)
        .setCompetitorId(competitorId)

fun CompetitorSelector.toDTO(selectorValue: Array<String>) = CompetitorSelectorDTO(id, applyToStageId, LogicalOperator.values()[logicalOperator],
        SelectorClassifier.values()[classifier], OperatorType.values()[operator], selectorValue)

fun StageDescriptor.toDTO(inputDescriptor: StageInputDescriptorDTO,
                          pointsAssignments: Array<PointsAssignmentDescriptorDTO>,
                          stageResultDescriptor: StageResultDescriptorDTO,
                          numberOfFights: Int): StageDescriptorDTO = StageDescriptorDTO()
        .setId(id)
        .setBracketType(BracketType.values()[bracketType])
        .setCompetitionId(competitionId)
        .setInputDescriptor(inputDescriptor)
        .setName(name)
        .setStageOrder(stageOrder)
        .setPointsAssignments(pointsAssignments)
        .setStageResultDescriptor(stageResultDescriptor)
        .setStageStatus(StageStatus.values()[stageStatus])
        .setWaitForPrevious(waitForPrevious)
        .setStageType(StageType.values()[stageType])
        .setHasThirdPlaceFight(hasThirdPlaceFight)
        .setCategoryId(categoryId)
        .setNumberOfFights(numberOfFights)


fun PointsAssignmentDescriptor.toDTO() = PointsAssignmentDescriptorDTO(id, CompetitorResultType.values()[classifier], points, additionalPoints)