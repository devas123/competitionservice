package compman.compsrv.mapping

import com.compmanager.compservice.jooq.tables.pojos.*
import com.compmanager.compservice.jooq.tables.records.CompScoreRecord
import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.util.IDGenerator
import java.sql.Timestamp


fun SchedulePeriod.toDTO(scheduleEntries: Array<ScheduleEntryDTO>): PeriodDTO = PeriodDTO()
        .setId(id)
        .setName(name)
        .setScheduleEntries(scheduleEntries)
        .setStartTime(startTime?.toInstant())



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

fun CompScore.toDTO(): CompScoreDTO = CompScoreDTO().setScore(ScoreDTO()
        .setPoints(points)
        .setAdvantages(advantages)
        .setPenalties(penalties))
        .setCompetitorId(compscoreCompetitorId)

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


fun CompScoreDTO.toRecord(fightId: String): CompScoreRecord =
        CompScoreRecord().also {
            it.compScoreOrder = this.order
            it.advantages = this.score?.advantages
            it.points = this.score?.points
            it.penalties = this.score?.penalties
            it.placeholderId = this.placeholderId
            it.compscoreCompetitorId = this.competitorId
            it.compscoreFightDescriptionId = fightId
        }

fun FightDescriptionDTO.toPojo(): FightDescription =
        FightDescription().also {
            it.id = this.id
            it.categoryId = this.categoryId
            it.reason = this.fightResult?.reason
            it.winnerId = this.fightResult?.winnerId
            it.resultType = this.fightResult?.resultTypeId
            it.duration = this.duration
            it.winFight = this.winFight
            it.loseFight = this.loseFight
            it.matId = this.mat?.id
            it.numberOnMat = this.numberOnMat
            it.numberInRound = this.numberInRound
            it.parent_1FightId = this.parentId1?.fightId
            it.parent_2FightId = this.parentId2?.fightId
            it.parent_1ReferenceType = this.parentId1?.referenceType?.ordinal
            it.parent_2ReferenceType = this.parentId2?.referenceType?.ordinal
            it.startTime = this.startTime?.let { Timestamp.from(it) }
            it.fightName = this.fightName
            it.stageId = this.stageId
            it.status = this.status?.ordinal
            it.round = this.round
            it.roundType = this.roundType?.ordinal
            it.groupId = this.groupId
            it.invalid = this.invalid
        }