package compman.compsrv.mapping

import com.compmanager.compservice.jooq.tables.pojos.*
import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.util.IDGenerator

fun SchedulePeriodProperties.toDTO(categories: Array<String>?): PeriodPropertiesDTO = PeriodPropertiesDTO()
        .setId(id)
        .setName(name)
        .setStartTime(startTime?.toInstant())
        .setTimeBetweenFights(timeBetweenFights)
        .setRiskPercent(riskPercent)
        .setCategories(categories)


fun ScheduleEntry.toDTO(): ScheduleEntryDTO = ScheduleEntryDTO()
        .setCategoryId(categoryId)
        .setFightId(fightId)
        .setMatId(matId)
        .setOrder(scheduleOrder)
        .setEndTime(endTime?.toInstant())
        .setEntryType(entryType?.let { ScheduleEntryType.values()[it] })
        .setStartTime(startTime?.toInstant())
        .setNumberOfFights(numberOfFights)
        .setFightDuration(fightDuration)

fun SchedulePeriod.toDTO(scheduleEntries: Array<ScheduleEntryDTO>, mats: Array<MatDescriptionDTO>): PeriodDTO = PeriodDTO()
        .setId(id)
        .setName(name)
        .setSchedule(scheduleEntries)
        .setStartTime(startTime?.toInstant())
        .setMats(mats)


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
        .setCompetitor(getCompetitor(compscoreCompetitorId))

fun CompetitionProperties.toDTO(staffIds: Array<String>?, promoCodes: Array<PromoCodeDTO>?, getRegistrationInfo: (id: String) -> RegistrationInfoDTO?): CompetitionPropertiesDTO =
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

fun MatDescription.toDTO(): MatDescriptionDTO = MatDescriptionDTO().setId(id).setName(name).setPeriodId(periodId).setMatOrder(matOrder)

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

fun CompetitorStageResult.toDTO(): CompetitorStageResultDTO = CompetitorStageResultDTO()
        .setStageId(stageId)
        .setPoints(points)
        .setGroupId(groupId)
        .setPlace(place)
        .setRound(round)
        .setCompetitorId(competitorId)

fun CompetitorSelector.toDTO(selectorValue: Array<String>) = CompetitorSelectorDTO(id, applyToStageId, LogicalOperator.values()[logicalOperator],
        SelectorClassifier.values()[classifier], OperatorType.values()[operator], selectorValue)




fun FightResultOption.toDTO(): FightResultOptionDTO = FightResultOptionDTO()
        .setId(id)
        .setDescription(description)
        .setShortName(shortName)
        .setDraw(draw)
        .setWinnerAdditionalPoints(winnerAdditionalPoints)
        .setLoserAdditionalPoints(loserAdditionalPoints)
        .setWinnerPoints(winnerPoints)
        .setLoserPoints(loserPoints)