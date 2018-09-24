package compman.compsrv.model.competition

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonView
import compman.compsrv.model.schedule.Schedule
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceConstructor
import java.math.BigDecimal
import java.util.*

data class CompetitionProperties @PersistenceConstructor @JsonCreator
constructor(
        @Id @JsonView(Views.Short::class) @JsonProperty("competitionId") val competitionId: String,
        @JsonView(Views.Short::class) @JsonProperty("creatorId") val creatorId: String,
        @JsonView(Views.Short::class) @JsonProperty("staffIds") val staffIds: Array<String>,
        @JsonView(Views.Short::class) @JsonProperty("emailNotificationsEnabled") val emailNotificationsEnabled: Boolean?,
        @JsonView(Views.Short::class) @JsonProperty("competitionName") val competitionName: String,
        @JsonView(Views.Public::class) @JsonProperty("registrationFee") val registrationFee: BigDecimal?,
        @JsonView(Views.Public::class) @JsonProperty("emailTemplate") val emailTemplate: String?,
        @JsonView(Views.Public::class) @JsonProperty("promoCodes") val promoCodes: List<PromoCode>?,
        @JsonView(Views.Short::class) @JsonProperty("startDate") val startDate: Date?,
        @JsonView(Views.Public::class) @JsonProperty("schedulePublished") val schedulePublished: Boolean,
        @JsonView(Views.Public::class) @JsonProperty("bracketsPublished") val bracketsPublished: Boolean,
        @JsonView(Views.Public::class) @JsonProperty("schedule") val schedule: Schedule?,
        @JsonView(Views.Public::class) @JsonProperty("status") val status: CompetitionStatus,
        @JsonView(Views.Short::class) @JsonProperty("endDate") val endDate: Date?,
        @JsonView(Views.Internal::class) @JsonProperty("registeredIds") val registeredIds: Set<String>,
        @JsonView(Views.Short::class) @JsonProperty("categories") val categories: Set<Category>?,
        @JsonView(Views.Short::class) @JsonProperty("mats") val mats: Set<String>?,
        @JsonView(Views.Short::class) @JsonProperty("registrationOpen") val registrationOpen: Boolean?) {
    constructor(competitionId: String, competitionName: String, creatorId: String) : this(
            creatorId = creatorId,
            staffIds = emptyArray<String>(),
            competitionId = competitionId,
            competitionName = competitionName,
            emailTemplate = null,
            registrationFee = null,
            emailNotificationsEnabled = false,
            endDate = null,
            registrationOpen = false,
            startDate = null,
            schedule = null,
            bracketsPublished = false,
            schedulePublished = false,
            status = CompetitionStatus.CREATED,
            promoCodes = emptyList(),
            categories = emptySet(),
            mats = emptySet(),
            registeredIds = emptySet())

    fun setCompetitionId(competitionId: String) = if (competitionId.isNotBlank()) copy(competitionId = competitionId) else this

    fun setStartDate(startDate: Date) = copy(startDate = startDate)
    fun setEndDate(endDate: Date) = copy(endDate = endDate)
    fun setPromocodes(promoCodes: List<PromoCode>) = copy(promoCodes = promoCodes)

    private fun parseDate(date: Any?, default: Date?) = if (date != null && !date.toString().isBlank()) {
        Date(date.toString().toLong())
    } else {
        default
    }

    fun applyProperties(props: Map<String, Any?>?) = if (props != null) copy(
            bracketsPublished = props["bracketsPublished"] as? Boolean ?: bracketsPublished,
            registrationOpen = props["registrationOpen"] as? Boolean ?: registrationOpen,
            startDate = parseDate(props["startDate"], startDate),
            endDate = parseDate(props["endDate"], endDate),
            emailNotificationsEnabled = props["emailNotificationsEnabled"] as? Boolean ?: emailNotificationsEnabled,
            registrationFee = if (props["registrationFee"] != null) BigDecimal(props["registrationFee"].toString()) else {
                registrationFee
            },
            competitionName = props["competitionName"] as String? ?: competitionName,
            emailTemplate = props["emailTemplate"] as? String ?: emailTemplate,
            schedulePublished = props["schedulePublished"] as? Boolean ?: schedulePublished,
            status = (props["status"] as? CompetitionStatus) ?: status) else {
        this
    }

    fun addCategory(category: Category) = copy(categories = (categories ?: emptySet()) + category)
    fun deleteCategory(categoryId: String) = copy(categories = (categories
            ?: emptySet()).filter { it.categoryId != categoryId }.toSet())

    fun addRegisteredId(id: String) = copy(registeredIds = registeredIds + id)
    fun deleteRegisteredId(id: String) = copy(registeredIds = registeredIds - id)

    fun setStatus(status: CompetitionStatus) = copy(status = status)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompetitionProperties

        if (competitionId != other.competitionId) return false
        if (creatorId != other.creatorId) return false
        if (!Arrays.equals(staffIds, other.staffIds)) return false
        if (emailNotificationsEnabled != other.emailNotificationsEnabled) return false
        if (competitionName != other.competitionName) return false
        if (registrationFee != other.registrationFee) return false
        if (emailTemplate != other.emailTemplate) return false
        if (promoCodes != other.promoCodes) return false
        if (startDate != other.startDate) return false
        if (schedulePublished != other.schedulePublished) return false
        if (bracketsPublished != other.bracketsPublished) return false
        if (status != other.status) return false
        if (endDate != other.endDate) return false
        if (registrationOpen != other.registrationOpen) return false

        return true
    }

    override fun hashCode(): Int {
        var result = competitionId.hashCode()
        result = 31 * result + creatorId.hashCode()
        result = 31 * result + Arrays.hashCode(staffIds)
        result = 31 * result + (emailNotificationsEnabled?.hashCode() ?: 0)
        result = 31 * result + competitionName.hashCode()
        result = 31 * result + (registrationFee?.hashCode() ?: 0)
        result = 31 * result + (emailTemplate?.hashCode() ?: 0)
        result = 31 * result + (promoCodes?.hashCode() ?: 0)
        result = 31 * result + (startDate?.hashCode() ?: 0)
        result = 31 * result + schedulePublished.hashCode()
        result = 31 * result + bracketsPublished.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (endDate?.hashCode() ?: 0)
        result = 31 * result + (registrationOpen?.hashCode() ?: 0)
        return result
    }

    fun setSchedule(schedule: Schedule?) = copy(schedule = schedule)
}