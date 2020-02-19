package compman.compsrv.util

import com.fasterxml.jackson.databind.ObjectMapper
import compman.compsrv.model.commands.CommandDTO
import compman.compsrv.model.dto.brackets.ParentFightReferenceDTO
import compman.compsrv.model.dto.brackets.StageRoundType
import compman.compsrv.model.dto.competition.*
import compman.compsrv.model.dto.dashboard.MatDescriptionDTO
import compman.compsrv.model.events.EventDTO
import compman.compsrv.model.events.EventType
import compman.compsrv.model.events.payload.ErrorEventPayload
import java.math.BigDecimal
import java.time.Instant

private fun parseDate(date: Any?, default: Instant?) = if (date != null && !date.toString().isBlank()) {
    Instant.ofEpochMilli(date.toString().toLong())
} else {
    default
}

fun getId(name: String) = IDGenerator.hashString(name)

fun CompetitorDTO.copy(id: String? = this.id,
                       email: String? = this.email,
                       userId: String? = this.userId,
                       firstName: String? = this.firstName,
                       lastName: String? = this.lastName,
                       birthDate: Instant? = this.birthDate,
                       academy: AcademyDTO? = this.academy,
                       categories: Array<String>? = this.categories,
                       competitionId: String? = this.competitionId,
                       registrationStatus: String? = this.registrationStatus,
                       promo: String? = this.promo) = CompetitorDTO()
        .setId(id)
        .setEmail(email)
        .setUserId(userId)
        .setFirstName(firstName)
        .setLastName(lastName)
        .setBirthDate(birthDate)
        .setAcademy(academy)
        .setCategories(categories)
        .setCompetitionId(competitionId)
        .setRegistrationStatus(registrationStatus)
        .setPromo(promo)

fun CompetitionPropertiesDTO.applyProperties(props: Map<String, Any?>?) = CompetitionPropertiesDTO().also {
    if (props != null) {
        bracketsPublished = props["bracketsPublished"] as? Boolean ?: this.bracketsPublished
        startDate = parseDate(props["startDate"], this.startDate)
        endDate = parseDate(props["endDate"], this.endDate)
        emailNotificationsEnabled = props["emailNotificationsEnabled"] as? Boolean ?: this.emailNotificationsEnabled
        competitionName = props["competitionName"] as String? ?: this.competitionName
        emailTemplate = props["emailTemplate"] as? String ?: this.emailTemplate
        schedulePublished = props["schedulePublished"] as? Boolean ?: this.schedulePublished
        timeZone = props["timeZone"]?.toString() ?: this.timeZone
    }
}

fun compNotEmpty(comp: CompetitorDTO?): Boolean {
    if (comp == null) return false
    val firstName = comp.firstName
    val lastName = comp.lastName
    return firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty()
}

fun ObjectMapper.createErrorEvent(command: CommandDTO, error: String?): EventDTO = EventDTO()
        .setId(IDGenerator.uid())
        .setCategoryId(command.categoryId)
        .setCorrelationId(command.correlationId)
        .setCompetitionId(command.competitionId)
        .setMatId(command.matId)
        .setType(EventType.ERROR_EVENT)
        .setPayload(writeValueAsString(ErrorEventPayload(error, command.id)))
fun ObjectMapper.createErrorEvent(event: EventDTO, error: String?): EventDTO = EventDTO()
        .setId(IDGenerator.uid())
        .setCategoryId(event.categoryId)
        .setCorrelationId(event.correlationId)
        .setCompetitionId(event.competitionId)
        .setMatId(event.matId)
        .setType(EventType.ERROR_EVENT)
        .setPayload(writeValueAsString(ErrorEventPayload(error, event.id)))

fun ObjectMapper.createEvent(command: CommandDTO, type: EventType, payload: Any?): EventDTO =
        EventDTO()
                .setCategoryId(command.categoryId)
                .setCorrelationId(command.correlationId)
                .setCompetitionId(command.competitionId)
                .setMatId(command.matId)
                .setType(type)
                .setPayload(writeValueAsString(payload))

fun <T> ObjectMapper.getPayloadAs(event: EventDTO , clazz: Class<T>): T? {
    return event.payload?.let {
        readValue(it, clazz)
    }
}

fun <T> ObjectMapper.getPayloadFromString(payload: String?, clazz: Class<T>): T? {
    return payload?.let {
        readValue(it, clazz)
    }
}

fun <T> ObjectMapper.getPayloadAs(command: CommandDTO , clazz: Class<T>): T? {
    return command.payload?.let {
        convertValue(it, clazz)
    }
}

fun FightDescriptionDTO.copy(id: String = this.id,
                             categoryId: String = this.categoryId,
                             fightName: String? = this.fightName,
                             winFight: String? = this.winFight,
                             loseFight: String? = this.loseFight,
                             scores: Array<CompScoreDTO>? = this.scores,
                             parentId1: ParentFightReferenceDTO? = this.parentId1,
                             parentId2: ParentFightReferenceDTO? = this.parentId2,
                             duration: BigDecimal = this.duration,
                             round: Int = this.round,
                             roundType: StageRoundType = this.roundType,
                             status: FightStatus? = this.status,
                             fightResult: FightResultDTO? = this.fightResult,
                             mat: MatDescriptionDTO? = this.mat,
                             numberOnMat: Int? = this.numberOnMat,
                             priority: Int? = this.priority,
                             competitionId: String = this.competitionId,
                             period: String? = this.period,
                             startTime: Instant? = this.startTime,
                             numberInRound: Int? = this.numberInRound,
                             groupId: String? = this.groupId,
                             stageId: String? = this.stageId): FightDescriptionDTO = FightDescriptionDTO()
        .setId(id)
        .setFightName(fightName)
        .setCategoryId(categoryId)
        .setWinFight(winFight)
        .setLoseFight(loseFight)
        .setScores(scores)
        .setParentId1(parentId1)
        .setParentId2(parentId2)
        .setDuration(duration)
        .setCompetitionId(competitionId)
        .setRound(round)
        .setRoundType(roundType)
        .setStatus(status)
        .setStartTime(startTime)
        .setFightResult(fightResult)
        .setMat(mat)
        .setNumberOnMat(numberOnMat)
        .setNumberInRound(numberInRound)
        .setPriority(priority)
        .setPeriod(period)
        .setStageId(stageId)
        .setGroupId(groupId)

fun FightDescriptionDTO.pushCompetitor(competitor: CompetitorDTO): FightDescriptionDTO {
    if (competitor.id == "fake") {
        return this
    }
    val localScores = mutableListOf<CompScoreDTO>().apply { scores?.toList()?.let { this.addAll(it) } }
    if (localScores.size < 2) {
        localScores.add(CompScoreDTO().setCompetitor(competitor).setScore(ScoreDTO()).setOrder(localScores.size))
    } else {
        throw RuntimeException("Fight is already packed. Cannot add competitors")
    }
    return copy(scores = localScores.toTypedArray())
}




