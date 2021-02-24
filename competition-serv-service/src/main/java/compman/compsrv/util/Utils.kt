package compman.compsrv.util

import compman.compsrv.model.dto.competition.AcademyDTO
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import compman.compsrv.model.dto.competition.CompetitorDTO
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant

private fun parseDate(date: Any?, default: Instant? = null) = if (date != null && date.toString().isNotBlank()) {
    Instant.ofEpochMilli(date.toString().toLong())
} else {
    default
}

fun <T> List<T>.applyConditionalUpdate(condition: (T) -> Boolean, update: (T) -> T): List<T> {
    return this.map {
        if (condition(it)) {
            update(it)
        } else {
            it
        }
    }
}

inline fun <reified T> Array<T>.applyConditionalUpdateArray(condition: (T) -> Boolean, update: (T) -> T): Array<T> {
    val result = Array(this.size) { this[it] }
    var i = 0
    for (t in this) {
        if (condition(t)) {
            result[i++] = update(t)
        } else {
            result[i++] = t
        }
    }
    return result
}

fun <T> T?.toMonoOrEmpty(): Mono<T> = Mono.justOrEmpty(this)


inline fun <reified T> Array<out T>.applyConditionalUpdate(condition: (T) -> Boolean, update: (T) -> T): Array<out T> {
    return this.map {
        if (condition(it)) {
            update(it)
        } else {
            it
        }
    }.toTypedArray()
}

fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO


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
                       promo: String? = this.promo): CompetitorDTO = CompetitorDTO()
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

fun Instant.toTimestamp(): Timestamp = Timestamp.from(this)


fun CompetitionPropertiesDTO.applyProperties(props: Map<String, String?>?) =
        if (props != null) {
            this.also { cp ->
                cp.bracketsPublished = props["bracketsPublished"]?.toBoolean() ?: this.bracketsPublished
                cp.startDate = parseDate(props["startDate"]) ?: this.startDate
                cp.endDate = parseDate(props["endDate"]) ?: this.endDate
                cp.emailNotificationsEnabled = props["emailNotificationsEnabled"]?.toBoolean()
                        ?: this.emailNotificationsEnabled
                cp.competitionName = props["competitionName"] ?: this.competitionName
                cp.emailTemplate = props["emailTemplate"] ?: this.emailTemplate
                cp.schedulePublished = props["schedulePublished"]?.toBoolean() ?: this.schedulePublished
                cp.timeZone = props["timeZone"] ?: this.timeZone
            }
        } else {
            this
        }

fun compNotEmpty(comp: CompetitorDTO?): Boolean {
    if (comp == null) return false
    val firstName = comp.firstName
    val lastName = comp.lastName
    return firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty()
}





