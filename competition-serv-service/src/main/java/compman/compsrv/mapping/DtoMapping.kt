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


fun CompetitorDTO.toPojo() =
        Competitor().also { cmp ->
            cmp.id = this.id
            cmp.academyId = this.academy?.id
            cmp.academyName = this.academy?.name
            cmp.birthDate = this.birthDate?.let { Timestamp.from(it) }
            cmp.email = this.email
            cmp.firstName = this.firstName
            cmp.lastName = this.lastName
            cmp.competitionId = this.competitionId
            cmp.promo = this.promo
            cmp.registrationStatus = this.registrationStatus
            cmp.userId = this.userId
        }

fun SchedulePeriod.toDTO(scheduleEntries: Array<ScheduleEntryDTO>): PeriodDTO = PeriodDTO()
        .setId(id)
        .setName(name)
        .setScheduleEntries(scheduleEntries)
        .setStartTime(startTime?.toInstant())



fun CategoryRestriction.toDTO(): CategoryRestrictionDTO = CategoryRestrictionDTO().setMaxValue(maxValue).setMinValue(minValue).setName(name).setType(CategoryRestrictionType.valueOf(type))
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
fun FightResultOption.toDTO(): FightResultOptionDTO =
        FightResultOptionDTO().setShortName(shortName)
                .setDescription(description)
                .setDraw(draw)
                .setId(id)
                .setLoserAdditionalPoints(loserAdditionalPoints)
                .setLoserPoints(loserPoints)
                .setWinnerAdditionalPoints(winnerAdditionalPoints)
                .setWinnerPoints(winnerPoints)

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
                .setStatus(CompetitionStatus.valueOf(status))

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
        .setRegistrationStatus(registrationStatus?.let { RegistrationStatus.valueOf(it).name })
        .setUserId(userId)

fun CategoryDescriptor.toDTO(competitors: Array<String>, restrictions: Array<CategoryRestrictionDTO>): CategoryDescriptorDTO = CategoryDescriptorDTO()
        .setId(id)
        .setFightDuration(fightDuration)
        .setCompetitors(competitors)
        .setRestrictions(restrictions)
        .setName(name).setRegistrationOpen(registrationOpen)

fun StageInputDescriptor.toDTO(selectors: Array<CompetitorSelectorDTO>): StageInputDescriptorDTO = StageInputDescriptorDTO()
        .setId(id)
        .setDistributionType(DistributionType.valueOf(distributionType))
        .setNumberOfCompetitors(numberOfCompetitors).setSelectors(selectors)

fun CompetitorStageResult.toDTO(): CompetitorStageResultDTO = CompetitorStageResultDTO()
        .setStageId(stageId)
        .setPoints(points)
        .setGroupId(groupId)
        .setPlace(place)
        .setRound(round)
        .setCompetitorId(competitorId)

fun CompetitorSelector.toDTO(selectorValue: Array<String>) = CompetitorSelectorDTO(id, applyToStageId, LogicalOperator.valueOf(logicalOperator),
        SelectorClassifier.valueOf(classifier), OperatorType.valueOf(`operator`), selectorValue)


fun CompScoreDTO.toRecord(fightId: String): CompScoreRecord =
        CompScoreRecord().also {
            it.compScoreOrder = this.order
            it.advantages = this.score?.advantages
            it.points = this.score?.points
            it.penalties = this.score?.penalties
            it.placeholderId = this.placeholderId
            it.parentFightId = this.parentFightId
            it.parentReferenceType = this.parentReferenceType?.name
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
            it.startTime = this.startTime?.let { instant -> Timestamp.from(instant) }
            it.fightName = this.fightName
            it.stageId = this.stageId
            it.status = this.status?.name
            it.round = this.round
            it.roundType = this.roundType?.name
            it.groupId = this.groupId
            it.invalid = this.invalid
        }

fun CompScoreDTO.toPojo(fightId: String): CompScore {
    return CompScore().also {cs ->
        cs.placeholderId = this.placeholderId
        cs.advantages = this.score?.advantages
        cs.compScoreOrder = this.order
        cs.compscoreCompetitorId = this.competitorId
        cs.compscoreFightDescriptionId = fightId
        cs.parentFightId = this.parentFightId
        cs.parentReferenceType = this.parentReferenceType?.name
        cs.penalties = this.score?.penalties
        cs.points = this.score?.points
    }
}