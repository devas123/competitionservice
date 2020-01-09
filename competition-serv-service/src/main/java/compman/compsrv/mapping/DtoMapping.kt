package compman.compsrv.mapping

import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.jpa.Event
import compman.compsrv.jpa.brackets.BracketDescriptor
import compman.compsrv.jpa.competition.*
import compman.compsrv.jpa.dashboard.CompetitionDashboardState
import compman.compsrv.jpa.dashboard.DashboardPeriod
import compman.compsrv.jpa.dashboard.MatDescription
import compman.compsrv.jpa.schedule.*
import compman.compsrv.model.dto.brackets.BracketDescriptorDTO
import compman.compsrv.model.dto.brackets.BracketType
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.dto.schedule.*
import compman.compsrv.model.events.EventDTO
import compman.compsrv.service.ScheduleService
import compman.compsrv.util.IDGenerator
import java.time.Instant
import java.util.*

fun ScheduleProperties.toDTO(): SchedulePropertiesDTO = SchedulePropertiesDTO()
        .setCompetitionId(id)
        .setPeriodPropertiesList(periodPropertiesList?.mapNotNull { it?.toDTO() }?.toTypedArray() ?: emptyArray())

fun SchedulePropertiesDTO.toEntity(findCompetitor: (id: String) -> Competitor?) =
        ScheduleProperties(competitionId, periodPropertiesList?.mapNotNull { pp -> pp.toEntity(competitionId, findCompetitor) }
                ?: emptyList())

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

fun FightResult.toDTO(): FightResultDTO = FightResultDTO().setDraw(draw).setReason(reason).setWinnerId(winnerId)
fun FightResultDTO.toEntity() = FightResult(winnerId = winnerId, draw = draw, reason = winnerId)

fun CompScoreDTO.toEntity(findCategory: (id: String) -> CategoryDescriptor?) = CompScore(
        id = id ?: "${competitor.id}_${UUID.randomUUID()}",
        competitor = competitor?.toEntity(findCategory)
                ?: throw IllegalArgumentException("Competitor must not be null."),
        score = score.toEntity()
)

fun CompScore.toDTO(): CompScoreDTO = CompScoreDTO().setScore(score.toDTO()).setCompetitor(competitor.toDTO()).setId(id)

fun CompetitionState.toDTO(includeCompetitors: Boolean = false, includeBrackets: Boolean = false): CompetitionStateDTO =
        CompetitionStateDTO()
                .setCategories(categories.map { it.toDTO(includeCompetitors, includeBrackets, id!!) }.toTypedArray())
                .setCompetitionId(id)
                .setDashboardState(dashboardState?.toDTO())
                .setProperties(properties?.toDTO())
                .setSchedule(schedule?.toDTO())
                .setStatus(status)

fun CompetitionStateDTO.toEntity() = this.let { dto ->
    val properties = dto.properties.toEntity()
    CompetitionState(
            id = dto.competitionId,
            categories = mutableSetOf(),
            properties = properties,
            schedule = Schedule(competitionId, ScheduleProperties(competitionId, emptyList()), mutableListOf()),
            dashboardState = dto.dashboardState?.toEntity()
                    ?: CompetitionDashboardState(competitionId, emptySet()),
            status = dto.status
    ).apply {
        val findCategory = { id: String -> this.categories.find { categoryState -> categoryState.id == id }?.category }
        val allCompetitors = this.categories.flatMap { it.category?.competitors?.toList() ?: emptyList() }
        val findCompetitor = { id: String -> allCompetitors.find { competitor -> competitor.id == id } }
        val fights = dto.categories?.flatMap {
            it.brackets?.fights?.toList() ?: emptyList()
        }?.mapNotNull { it.toEntity(findCategory) }
                ?: emptyList()
        categories = dto.categories.mapNotNull { it?.toEntity(this, findCompetitor) }.toMutableSet()
        schedule = dto.schedule?.toEntity({ fightId -> fights.first { it.id == fightId } }, findCompetitor)
                ?: this.schedule
    }
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

fun CompetitionProperties.toDTO(): CompetitionPropertiesDTO =
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

fun CategoryState.toDTO(includeCompetitors: Boolean = false, includeBrackets: Boolean = false, competitionId: String): CategoryStateDTO = CategoryStateDTO().setId(id)
        .setCompetitionId(competitionId)
        .setCategory(category?.toDTO())
        .setStatus(status)
        .setBrackets(if (includeBrackets) brackets?.toDTO { category?.toDTO() } else null)
        .setNumberOfCompetitors(category?.competitors?.size ?: 0)
        .setCompetitors(if (includeCompetitors) category?.competitors?.map { it.toDTO() }?.toTypedArray() else emptyArray())
        .setFightsNumber(brackets?.fights?.filter { !ScheduleService.obsoleteFight(it, category?.competitors?.size == 0) }?.size
                ?: 0)

private fun emptyBracketDescriptor(id: String, competitionId: String) = BracketDescriptor(id, competitionId, BracketType.SINGLE_ELIMINATION, mutableListOf())

fun EventDTO.toEntity(): Event = Event(id, type, competitionId, correlationId, categoryId, matId, payload)

fun CategoryStateDTO.toEntity(competition: CompetitionState, findCompetitor: (id: String) -> Competitor?): CategoryState {
    val cat = category.toEntity(competition.id!!, findCompetitor)
    return CategoryState(
            id = id,
            category = cat,
            status = status,
            brackets = brackets?.toEntity { cat } ?: emptyBracketDescriptor(id, competitionId)
    )
}


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


fun BracketDescriptorDTO.toEntity(findCategory: (id: String) -> CategoryDescriptor?) = BracketDescriptor(id, competitionId, bracketType, fights.mapNotNull { f -> f?.toEntity(findCategory) }.toMutableList())

fun BracketDescriptor.toDTO(getCategory: (id: String) -> CategoryDescriptorDTO?): BracketDescriptorDTO? = BracketDescriptorDTO(id, competitionId, bracketType, fights?.map { it.toDTO(getCategory) }?.toTypedArray()
        ?: emptyArray())

fun FightDescriptionDTO.toEntity(findCategory: (id: String) -> CategoryDescriptor?) = FightDescription(
        id = id,
        categoryId = category?.id!!,
        winFight = winFight,
        loseFight = loseFight,
        scores = scores?.mapNotNull { it?.toEntity(findCategory) }?.toMutableList()
                ?: mutableListOf(),
        parentId1 = parentId1,
        parentId2 = parentId2,
        duration = duration,
        round = round,
        stage = stage,
        fightResult = fightResult?.toEntity(),
        matId = matId,
        numberOnMat = numberOnMat,
        priority = priority,
        competitionId = competitionId,
        period = period,
        startTime = startTime,
        numberInRound = numberInRound
)

fun FightDescription.toDTO(getCategory: (id: String) -> CategoryDescriptorDTO?) = FightDescriptionDTO(
        id,
        getCategory(categoryId),
        winFight,
        loseFight,
        scores?.map { it.toDTO() }?.toTypedArray(),
        parentId1,
        parentId2,
        duration,
        round,
        stage,
        fightResult?.toDTO(),
        matId,
        numberOnMat,
        priority,
        competitionId,
        period,
        startTime,
        numberInRound
)