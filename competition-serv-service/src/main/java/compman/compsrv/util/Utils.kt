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

fun Boolean?.orFalse() = this == true

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





