package compman.compsrv.mapping

import com.compmanager.compservice.jooq.tables.pojos.Schedule
import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.brackets.*
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.model.events.EventDTO
import compman.compsrv.service.ScheduleService
import compman.compsrv.util.IDGenerator
import java.time.Instant
import java.util.*

fun PeriodProperties.toDTO(): PeriodPropertiesDTO = PeriodPropertiesDTO()
        .setId(id)
        .setName(name)
        .setStartTime(startTime)
        .setNumberOfMats(numberOfMats)
        .setTimeBetweenFights(timeBetweenFights)
        .setRiskPercent(riskPercent)
        .setCategories(categories.map { it.toDTO() }.toTypedArray())

fun PeriodPropertiesDTO.toEntity(competitionId: String, findCompetitor: (id: String) -> Competitor?): PeriodProperties = PeriodProperties(id,
        name,
        startTime,
        numberOfMats,
        timeBetweenFights,
        riskPercent,
        categories.mapNotNull { it.toEntity(competitionId, findCompetitor) })


fun ScheduleEntry.toDTO(): ScheduleEntryDTO = ScheduleEntryDTO()
        .setCategoryId(categoryId)
        .setStartTime(startTime)
        .setNumberOfFights(numberOfFights)
        .setFightDuration(fightDuration)

fun ScheduleEntryDTO.toEntity() = ScheduleEntry(
        categoryId = categoryId,
        startTime = startTime,
        numberOfFights = numberOfFights,
        fightDuration = fightDuration
)


fun Schedule.toDTO(): ScheduleDTO = ScheduleDTO()
        .setId(id)
        .setPeriods(periods?.map { it.toDTO() }?.toTypedArray())
        .setScheduleProperties(scheduleProperties?.toDTO())

fun ScheduleDTO.toEntity(getFight: (fightId: String) -> FightDescription, findCompetitor: (id: String) -> Competitor?) = Schedule(
        id = id,
        scheduleProperties = scheduleProperties.toEntity(findCompetitor),
        periods = periods.mapNotNull { it.toEntity(scheduleProperties.competitionId, getFight, findCompetitor) }.toMutableList()
)


fun Period.toDTO(): PeriodDTO = PeriodDTO()
        .setId(id)
        .setName(name)
        .setSchedule(schedule.map { it.toDTO() }.toTypedArray())
        .setCategories(categories.map { it.toDTO() }.toTypedArray())
        .setStartTime(startTime)
        .setNumberOfMats(numberOfMats)
        .setFightsByMats(fightsByMats?.map { it.toDTO() }?.toTypedArray())

fun PeriodDTO.toEntity(competitionId: String, getFight: (fightId: String) -> FightDescription, findCompetitor: (id: String) -> Competitor?) = Period(
        id = id,
        name = name,
        schedule = schedule.mapNotNull { it.toEntity() },
        categories = categories.mapNotNull { it.toEntity(competitionId, findCompetitor) },
        startTime = startTime,
        numberOfMats = numberOfMats,
        fightsByMats = fightsByMats?.mapNotNull { it.toEntity(getFight) }
)


fun MatScheduleContainer.toDTO(): MatScheduleContainerDTO = MatScheduleContainerDTO()
        .setTotalFights(totalFights)
        .setId(id)
        .setFights(fights.map { it.toDTO(id ?: "") }.toTypedArray())

fun MatScheduleContainerDTO.toEntity(getFight: (fightId: String) -> FightDescription) = MatScheduleContainer(Instant.now(), totalFights, id, fights.mapNotNull { it.toEntity { getFight(it.fightId) } }, ArrayList())
fun FightStartTimePair.toDTO(matId: String): FightStartTimePairDTO = FightStartTimePairDTO()
        .setFightId(fight.id)
        .setFightNumber(fightNumber)
        .setStartTime(startTime)
        .setMatId(matId)

fun FightStartTimePairDTO.toEntity(fight: () -> FightDescription) = FightStartTimePair(
        fight = fight(),
        fightNumber = fightNumber,
        startTime = startTime
)


fun CategoryRestriction.toDTO(): CategoryRestrictionDTO = CategoryRestrictionDTO().setId(IDGenerator.restrictionId(this)).setMaxValue(maxValue).setMinValue(minValue).setName(name).setType(type).setUnit(unit)

fun CategoryRestrictionDTO.toEntity() = CategoryRestriction(IDGenerator.restrictionId(this), type, name, minValue, maxValue, unit, mutableSetOf())

fun Score.toDTO() = ScoreDTO(points, advantages, penalties)

fun ScoreDTO.toEntity() = Score(advantages = advantages, points = points, penalties = penalties)

fun RegistrationInfo.toDTO(): RegistrationInfoDTO = RegistrationInfoDTO()
        .setId(id)
        .setRegistrationOpen(registrationOpen)
        .setRegistrationPeriods(registrationPeriods.map { it.toDTO() }.toTypedArray())
        .setRegistrationGroups(registrationGroups.map { it.toDTO() }.toTypedArray())

fun RegistrationInfoDTO.toEntity() = this.let { dto ->
    RegistrationInfo().apply {
        id = dto.id
        registrationOpen = dto.registrationOpen
        registrationPeriods = dto.registrationPeriods?.mapNotNull {
            it.toEntity({ id ->
                dto.registrationGroups.find { groupDTO -> groupDTO.id == id }?.toEntity { this }
                        ?: throw IllegalArgumentException("No group with id $id")
            }, { this })
        }?.toMutableSet() ?: mutableSetOf()
        registrationGroups = dto.registrationGroups?.mapNotNull { it.toEntity { this } }?.toMutableSet()
                ?: mutableSetOf()
    }
}

fun RegistrationGroup.toDTO(): RegistrationGroupDTO = RegistrationGroupDTO()
        .setRegistrationPeriodIds(registrationPeriods?.map { it.id }?.toTypedArray())
        .setDisplayName(displayName)
        .setId(id)
        .setRegistrationFee(registrationFee)
        .setCategories(categories?.toTypedArray())

fun RegistrationGroupDTO.toEntity(getRegistrationInfo: () -> RegistrationInfo) = RegistrationGroup(id, getRegistrationInfo(), defaultGroup
        ?: false, null, displayName, registrationFee, categories?.toMutableSet()
        ?: mutableSetOf())

fun RegistrationPeriod.toDTO(): RegistrationPeriodDTO = RegistrationPeriodDTO()
        .setId(id)
        .setCompetitionId(registrationInfo?.id)
        .setName(name)
        .setEnd(endDate)
        .setStart(startDate)
        .setRegistrationGroupIds(registrationGroups?.map { it.id }?.toTypedArray())

fun RegistrationPeriodDTO.toEntity(getRegistrationGroup: (id: String) -> RegistrationGroup, getRegistrationInfo: (id: String) -> RegistrationInfo) =
        RegistrationPeriod(id, name, getRegistrationInfo(competitionId), start, end, registrationGroupIds?.map { getRegistrationGroup(it) }?.toMutableSet()
                ?: mutableSetOf())

fun PromoCode.toDTO(): PromoCodeDTO = PromoCodeDTO()
        .setId(id.toString())
        .setCoefficient(coefficient)
        .setCompetitionId(competitionId)
        .setStartAt(startAt)
        .setExpireAt(expireAt)

fun PromoCodeDTO.toEntity() = PromoCode(id?.toLong(), coefficient, competitionId, startAt, expireAt)

fun FightResult.toDTO(): FightResultDTO = FightResultDTO().setResultType(resultType).setReason(reason).setWinnerId(winnerId)
fun FightResultDTO.toEntity() = FightResult(winnerId = winnerId, resultType = resultType, reason = reason)

fun CompScoreDTO.toEntity(findCategory: (id: String) -> CategoryDescriptor?) = CompScore(
        id = id ?: IDGenerator.compScoreId(competitor.id),
        competitor = competitor?.toEntity(findCategory)
                ?: throw IllegalArgumentException("Competitor must not be null."),
        score = score.toEntity()
)

fun CompScore.toDTO(): CompScoreDTO = CompScoreDTO().setScore(score.toDTO()).setCompetitor(competitor.toDTO()).setId(id)

fun CompetitionState.toDTO(includeCompetitors: Boolean = false, includeBrackets: Boolean = false): CompetitionStateDTO {
    return CompetitionStateDTO()
            .setCategories(categories.map { it.toDTO(includeCompetitors, includeBrackets, id!!) }.toTypedArray())
            .setCompetitionId(id)
            .setDashboardState(dashboardState?.toDTO())
            .setProperties(properties?.toDTO(status))
            .setSchedule(schedule?.toDTO())
            .setStatus(status)
}

fun CompetitionPropertiesDTO.toEntity() = CompetitionProperties(id = id,
        creatorId = creatorId,
        staffIds = staffIds?.toMutableSet() ?: mutableSetOf(),
        emailNotificationsEnabled = emailNotificationsEnabled,
        competitionName = competitionName,
        emailTemplate = emailTemplate,
        promoCodes = promoCodes?.mapNotNull { it.toEntity() } ?: emptyList(),
        startDate = startDate,
        schedulePublished = schedulePublished ?: false,
        bracketsPublished = bracketsPublished ?: false,
        endDate = endDate,
        timeZone = timeZone ?: TimeZone.getDefault().id,
        registrationInfo = registrationInfo?.let {
            if (it.id.isNullOrBlank()) {
                it.id = id
            }
            it.toEntity()
        } ?: RegistrationInfo(id, false, mutableSetOf()),
        creationTimestamp = creationTimestamp ?: System.currentTimeMillis())

fun CompetitionProperties.toDTO(status: CompetitionStatus?): CompetitionPropertiesDTO =
        CompetitionPropertiesDTO()
                .setId(id)
                .setBracketsPublished(bracketsPublished)
                .setSchedulePublished(schedulePublished)
                .setCreatorId(creatorId)
                .setCompetitionName(competitionName)
                .setEmailNotificationsEnabled(emailNotificationsEnabled)
                .setEmailTemplate(emailTemplate)
                .setEndDate(endDate)
                .setStartDate(startDate)
                .setStaffIds(staffIds.toTypedArray())
                .setPromoCodes(promoCodes?.map { it.toDTO() })
                .setTimeZone(timeZone)
                .setRegistrationInfo(registrationInfo.toDTO())
                .setCreationTimestamp(creationTimestamp)
                .setStatus(status)

fun DashboardPeriodDTO.toEntity() = DashboardPeriod(id, name, mutableListOf(), startTime, isActive)
        .also { per -> per.mats = mats?.map { it.toEntity().apply { dashboardPeriod = per } }?.toMutableList() }

fun DashboardPeriod.toDTO(): DashboardPeriodDTO = DashboardPeriodDTO()
        .setId(id)
        .setName(name)
        .setMats(mats?.map { it.toDTO() }?.toTypedArray() ?: emptyArray())
        .setStartTime(startTime)
        .setIsActive(isActive)

fun MatDescription.toDTO(): MatDescriptionDTO = MatDescriptionDTO().setId(id).setName(name).setDashboardPeriodId(dashboardPeriod?.id)
fun MatDescriptionDTO.toEntity() = MatDescription(id, name, null)

fun CompetitionDashboardState.toDTO(): CompetitionDashboardStateDTO = CompetitionDashboardStateDTO()
        .setCompetitionId(id)
        .setPeriods(periods.map { it.toDTO() }.toTypedArray())

fun CompetitionDashboardStateDTO.toEntity() =
        CompetitionDashboardState(competitionId, periods.mapNotNull { it.toEntity() }.toSet())

fun CompetitorDTO.toEntity(findCategory: (id: String) -> CategoryDescriptor?) = Competitor(
        id = id,
        email = email,
        userId = userId,
        firstName = firstName,
        lastName = lastName,
        birthDate = birthDate,
        academy = academy?.toEntity(),
        categories = categories?.mapNotNull { findCategory(it) }?.toMutableSet(),
        competitionId = competitionId,
        registrationStatus = RegistrationStatus.valueOf(registrationStatus),
        promo = promo
)

fun Competitor.toDTO(): CompetitorDTO = CompetitorDTO()
        .setId(id)
        .setAcademy(academy?.toDTO())
        .setBirthDate(birthDate)
        .setCategories(categories?.map { it.id }?.toTypedArray())
        .setCompetitionId(competitionId)
        .setEmail(email)
        .setFirstName(firstName)
        .setLastName(lastName)
        .setPromo(promo)
        .setRegistrationStatus(registrationStatus?.name)
        .setUserId(userId)

fun Academy.toDTO(): AcademyDTO = AcademyDTO().setId(id).setName(name)
fun AcademyDTO.toEntity(): Academy = Academy(id, name)

fun BracketDescriptor.toDTO(): BracketDescriptorDTO = BracketDescriptorDTO()
        .setId(id)
        .setCompetitionId(competitionId)
        .setStages(stages?.map { it.toDTO() }?.toTypedArray())

fun CategoryState.toDTO(includeCompetitors: Boolean = false, includeBrackets: Boolean = false, competitionId: String): CategoryStateDTO = CategoryStateDTO().setId(id)
        .setCompetitionId(competitionId)
        .setCategory(category?.toDTO())
        .setStatus(status)
        .setBrackets(if (includeBrackets) brackets?.toDTO() else null)
        .setNumberOfCompetitors(category?.competitors?.size ?: 0)
        .setCompetitors(if (includeCompetitors) category?.competitors?.map { it.toDTO() }?.toTypedArray() else emptyArray())
        .setFightsNumber(brackets?.stages?.flatMap { it.fights ?: mutableListOf() }?.filter { !ScheduleService.obsoleteFight(it, category?.competitors?.size == 0) }?.size
                ?: 0)

private fun emptyBracketsDescriptor(id: String, competitionId: String) = BracketDescriptor(id, competitionId, mutableSetOf())

fun EventDTO.toEntity(): Event = Event(id, type, competitionId, correlationId, categoryId, matId, payload)

fun CategoryStateDTO.toEntity(competition: CompetitionState, findCompetitor: (id: String) -> Competitor?, findStageFights: (stageId: String?) -> MutableList<FightDescription>?): CategoryState {
    val cat = category.toEntity(competition.id!!, findCompetitor)
    return CategoryState(
            id = id,
            category = cat,
            status = status,
            brackets = brackets?.toEntity(findStageFights, findCompetitor) ?: emptyBracketsDescriptor(id, competitionId)
    )
}

fun BracketDescriptorDTO.toEntity(findStageFights: (stageId: String?) -> MutableList<FightDescription>?, findCompetitor: (id: String) -> Competitor?) = BracketDescriptor(id, competitionId, stages?.map { it.toEntity(findCompetitor, findStageFights) }?.toMutableSet())


fun CategoryDescriptorDTO.toEntity(competitionId: String, findCompetitor: (id: String) -> Competitor?) = CategoryDescriptor(
        competitionId = competitionId,
        restrictions = restrictions?.mapNotNull { it.toEntity() }?.toMutableSet(),
        id = id,
        fightDuration = fightDuration,
        competitors = competitors?.mapNotNull { findCompetitor(it) }?.toMutableSet(),
        name = name,
        registrationOpen = registrationOpen ?: true
)


fun CategoryDescriptor.toDTO(): CategoryDescriptorDTO = CategoryDescriptorDTO().setId(id).setFightDuration(fightDuration).setCompetitors(competitors?.mapNotNull { it.id }?.toTypedArray())
        .setRestrictions(restrictions?.map { it.toDTO() }?.toTypedArray()).setName(name).setRegistrationOpen(registrationOpen)

fun StageInputDescriptorDTO.toEntity() = StageInputDescriptor(id = id, distributionType = distributionType,
        numberOfCompetitors = numberOfCompetitors, selectors = selectors?.map { it.toEntity() }?.toMutableSet())

fun StageInputDescriptor.toDTO(): StageInputDescriptorDTO = StageInputDescriptorDTO().setId(id).setDistributionType(distributionType)
        .setNumberOfCompetitors(numberOfCompetitors).setSelectors(selectors?.map { it.toDTO() }?.toTypedArray())

fun StageResultDescriptorDTO.toEntity(findCompetitor: (id: String) -> Competitor?) = StageResultDescriptor(id = id, name = name, competitorResults = competitorResults?.map { it.toEntity(findCompetitor) }?.toMutableList())
fun StageResultDescriptor.toDTO(): StageResultDescriptorDTO = StageResultDescriptorDTO().setId(id).setName(name).setCompetitorResults(competitorResults?.map { it.toDTO() }?.toTypedArray())



fun CompetitorResultDTO.toEntity(findCompetitor: (id: String) -> Competitor?) = CompetitorResult(id = id, points = points, groupId = groupId, place = place, round = round, competitor = findCompetitor(competitorId), stageResultDescriptors = mutableSetOf())
fun CompetitorResult.toDTO(): CompetitorResultDTO = CompetitorResultDTO()
        .setId(id)
        .setPoints(points)
        .setGroupId(groupId)
        .setPlace(place)
        .setRound(round)
        .setCompetitorId(competitor?.id)

fun CompetitorSelectorDTO.toEntity() = CompetitorSelector(id = id, applyToStageId = applyToStageId, classifier = classifier, logicalOperator = logicalOperator, operator = operator, selectorValue = selectorValue?.toList())
fun CompetitorSelector.toDTO() = CompetitorSelectorDTO(id, applyToStageId, logicalOperator, classifier, operator, selectorValue?.toTypedArray())

fun StageDescriptorDTO.toEntity(findCompetitor: (id: String) -> Competitor?, findStageFights: (stageId: String?) -> MutableList<FightDescription>?) = StageDescriptor(
        id = id,
        competitionId = competitionId,
        bracketType = bracketType,
        fights = findStageFights(id),
        name = name,
        stageOrder = stageOrder,
        inputDescriptor = inputDescriptor?.toEntity(),
        pointsAssignments = pointsAssignments?.map { it.toEntity() }?.toMutableSet(),
        stageResultDescriptor = stageResultDescriptor?.toEntity(findCompetitor),
        stageStatus = stageStatus,
        stageType = stageType,
        hasThirdPlaceFight = hasThirdPlaceFight,
        waitForPrevious = waitForPrevious,
        categoryId = categoryId)

fun StageDescriptor.toDTO(): StageDescriptorDTO = StageDescriptorDTO()
        .setId(id)
        .setBracketType(bracketType)
        .setCompetitionId(competitionId)
        .setInputDescriptor(inputDescriptor?.toDTO())
        .setName(name)
        .setStageOrder(stageOrder)
        .setPointsAssignments(pointsAssignments?.map { it.toDTO() }?.toTypedArray())
        .setStageResultDescriptor(stageResultDescriptor?.toDTO())
        .setStageStatus(stageStatus)
        .setWaitForPrevious(waitForPrevious)
        .setStageType(stageType)
        .setHasThirdPlaceFight(hasThirdPlaceFight)
        .setCategoryId(categoryId)
        .setNumberOfFights(fights?.size ?: 0)


fun ParentFightReferenceDTO.toEntity() = ParentFightReference(referenceType, fightId)
fun ParentFightReference.toDTO() = ParentFightReferenceDTO(referenceType, fightId)

fun PointsAssignmentDescriptorDTO.toEntity() = PointsAssignmentDescriptor(id, classifier, points, additionalPoints)
fun PointsAssignmentDescriptor.toDTO() = PointsAssignmentDescriptorDTO(id, classifier, points, additionalPoints)


fun FightDescriptionDTO.toEntity(findCategory: (id: String) -> CategoryDescriptor?) = FightDescription(
        id = id,
        categoryId = category?.id!!,
        fightName = fightName,
        winFight = winFight,
        loseFight = loseFight,
        scores = scores?.mapNotNull { it?.toEntity(findCategory) }?.toMutableList()
                ?: mutableListOf(),
        parentId1 = parentId1?.toEntity(),
        parentId2 = parentId2?.toEntity(),
        duration = duration,
        round = round,
        status = status,
        fightResult = fightResult?.toEntity(),
        matId = mat?.id,
        numberOnMat = numberOnMat,
        priority = priority,
        competitionId = competitionId,
        period = period,
        startTime = startTime,
        numberInRound = numberInRound,
        roundType = roundType
)

fun FightDescription.toDTO(getCategory: (id: String) -> CategoryDescriptorDTO?, getMat: (id: String?) -> MatDescriptionDTO?) = FightDescriptionDTO(
        id,
        getCategory(categoryId),
        fightName,
        winFight,
        loseFight,
        scores?.map { it.toDTO() }?.toTypedArray(),
        parentId1?.toDTO(),
        parentId2?.toDTO(),
        duration,
        round,
        roundType,
        status,
        fightResult?.toDTO(),
        getMat(matId),
        numberOnMat,
        priority,
        competitionId,
        period,
        startTime,
        numberInRound
)