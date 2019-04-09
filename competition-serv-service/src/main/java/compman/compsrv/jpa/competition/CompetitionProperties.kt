package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.CompetitionPropertiesDTO
import java.time.Instant
import java.time.ZoneId
import java.util.*
import javax.persistence.*

@Entity(name = "competition_properties")
class CompetitionProperties(
        id: String,
        var creatorId: String,
        var staffIds: Array<String>,
        var emailNotificationsEnabled: Boolean?,
        var competitionName: String,
        var emailTemplate: String?,
        @OneToMany(mappedBy = "competitionId")
        var promoCodes: List<PromoCode>?,
        var startDate: Instant?,
        var schedulePublished: Boolean,
        var bracketsPublished: Boolean,
        var endDate: Instant?,
        var timeZone: String,
        @OneToOne(cascade = [CascadeType.ALL], optional = false)
        @PrimaryKeyJoinColumn
        var registrationInfo: RegistrationInfo) : AbstractJpaPersistable<String>(id) {

    companion object {
        fun fromDTO(dto: CompetitionPropertiesDTO): CompetitionProperties {
            return CompetitionProperties(id = dto.id,
                    creatorId = dto.creatorId,
                    staffIds = dto.staffIds ?: emptyArray(),
                    emailNotificationsEnabled = dto.emailNotificationsEnabled,
                    competitionName = dto.competitionName,
                    emailTemplate = dto.emailTemplate,
                    promoCodes = dto.promoCodes?.map { PromoCode.fromDTO(it) } ?: emptyList(),
                    startDate = dto.startDate,
                    schedulePublished = dto.schedulePublished ?: false,
                    bracketsPublished = dto.bracketsPublished ?: false,
                    endDate = dto.endDate,
                    timeZone = dto.timeZone ?: TimeZone.getDefault().id,
                    registrationInfo = dto.registrationInfo?.let {
                        if (it.id.isNullOrBlank()) {
                            it.id = dto.id
                        }
                        RegistrationInfo.fromDTO(it)
                    }
                            ?: RegistrationInfo(dto.id, false, mutableListOf()))
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
            startDate = Instant.now(),
            schedulePublished = false,
            bracketsPublished = false,
            endDate = Instant.now(),
            timeZone = ZoneId.systemDefault().id,
            registrationInfo = RegistrationInfo(competitionId, false, mutableListOf())
    )

    private fun parseDate(date: Any?, default: Instant?) = if (date != null && !date.toString().isBlank()) {
        Instant.ofEpochMilli(date.toString().toLong())
    } else {
        default
    }

    fun applyProperties(props: Map<String, Any?>?): CompetitionProperties {
        if (props != null) {
            bracketsPublished = props["bracketsPublished"] as? Boolean ?: bracketsPublished
            startDate = parseDate(props["startDate"], startDate)
            endDate = parseDate(props["endDate"], endDate)
            emailNotificationsEnabled = props["emailNotificationsEnabled"] as? Boolean ?: emailNotificationsEnabled
            competitionName = props["competitionName"] as String? ?: competitionName
            emailTemplate = props["emailTemplate"] as? String ?: emailTemplate
            schedulePublished = props["schedulePublished"] as? Boolean ?: schedulePublished
            timeZone = props["timeZone"]?.toString() ?: timeZone
        }
        return this
    }

    fun toDTO(): CompetitionPropertiesDTO = CompetitionPropertiesDTO()
            .setId(id)
            .setBracketsPublished(bracketsPublished)
            .setSchedulePublished(schedulePublished)
            .setCreatorId(creatorId)
            .setCompetitionName(competitionName)
            .setEmailNotificationsEnabled(emailNotificationsEnabled)
            .setEmailTemplate(emailTemplate)
            .setEndDate(endDate)
            .setStartDate(startDate)
            .setStaffIds(staffIds)
            .setPromoCodes(promoCodes?.map { it.toDTO() })
            .setTimeZone(timeZone)
            .setRegistrationInfo(registrationInfo.toDTO())

}