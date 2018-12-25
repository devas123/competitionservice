package compman.compsrv.jpa.competition

import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.persistence.*

@Entity
@Table(name = "competition_properties")
data class CompetitionProperties(
        @Id val id: String,
        val creatorId: String,
        val staffIds: Array<String>,
        val emailNotificationsEnabled: Boolean?,
        val competitionName: String,
        val emailTemplate: String?,
        @OneToMany
        @JoinColumn(name = "competitionId")
        val promoCodes: List<PromoCode>?,
        val startDate: ZonedDateTime?,
        val schedulePublished: Boolean,
        val bracketsPublished: Boolean,
        val endDate: ZonedDateTime?,
        val timeZone: String,
        @OneToOne(cascade = [CascadeType.ALL], optional = false)
        @MapsId
        val registrationInfo: RegistrationInfo) {

    companion object {
        fun fromDTO(dto: CompetitionPropertiesDTO): CompetitionProperties {
            return CompetitionProperties(id = dto.id, creatorId = dto.creatorId,
                    staffIds = dto.staffIds,
                    emailNotificationsEnabled = dto.emailNotificationsEnabled,
                    competitionName = dto.competitionName,
                    emailTemplate = dto.emailTemplate,
                    promoCodes = dto.promoCodes.map { PromoCode.fromDTO(it) },
                    startDate = dto.startDate,
                    schedulePublished = dto.schedulePublished,
                    bracketsPublished = dto.bracketsPublished,
                    endDate = dto.endDate,
                    timeZone = dto.timeZone,
                    registrationInfo = RegistrationInfo.fromDTO(dto.registrationInfo))
        }
    }

    constructor(competitionId: String, competitionName: String, creatorId: String) : this(
            id = competitionId,
            competitionName = competitionName,
            creatorId = creatorId,
            staffIds = emptyArray(),
            emailNotificationsEnabled = false,
            emailTemplate = null,
            promoCodes = emptyList(),
            startDate = ZonedDateTime.now(),
            schedulePublished = false,
            bracketsPublished = false,
            endDate = ZonedDateTime.now(),
            timeZone = ZoneId.systemDefault().id,
            registrationInfo = RegistrationInfo(competitionId, emptyArray())
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CompetitionProperties

        if (id != other.id) return false
        if (creatorId != other.creatorId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + creatorId.hashCode()
        return result
    }

    private fun parseDate(date: Any?, default: ZonedDateTime?) = if (date != null && !date.toString().isBlank()) {
        ZonedDateTime.ofInstant(Instant.ofEpochMilli(date.toString().toLong()), ZoneId.of(timeZone))
    } else {
        default
    }

    fun withStartDate(startDate: ZonedDateTime) = copy(startDate = startDate)
    fun withEndDate(endDate: ZonedDateTime) = copy(endDate = endDate)
    fun withPromocodes(promoCodes: List<PromoCode>) = copy(promoCodes = promoCodes)
    fun applyProperties(props: Map<String, Any?>?) = if (props != null) copy(
            bracketsPublished = props["bracketsPublished"] as? Boolean ?: bracketsPublished,
            startDate = parseDate(props["startDate"], startDate),
            endDate = parseDate(props["endDate"], endDate),
            emailNotificationsEnabled = props["emailNotificationsEnabled"] as? Boolean ?: emailNotificationsEnabled,
            competitionName = props["competitionName"] as String? ?: competitionName,
            emailTemplate = props["emailTemplate"] as? String ?: emailTemplate,
            schedulePublished = props["schedulePublished"] as? Boolean ?: schedulePublished,
            timeZone = props["timeZone"]?.toString() ?: timeZone) else {
        this
    }

}