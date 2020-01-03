package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import java.time.Instant
import java.time.ZoneId
import javax.persistence.*

@Entity(name = "competition_properties")
class CompetitionProperties(
        id: String,
        var creatorId: String,
        @ElementCollection
        @OrderColumn
        var staffIds: MutableSet<String>,
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
        @OneToOne(optional = false, orphanRemoval = true, fetch = FetchType.LAZY)
        @Cascade(CascadeType.ALL)
        @PrimaryKeyJoinColumn
        var registrationInfo: RegistrationInfo,
        @Column(nullable = false)
        var creationTimestamp: Long) : AbstractJpaPersistable<String>(id) {

    constructor(competitionId: String, competitionName: String, creatorId: String) : this(
            id = competitionId,
            competitionName = competitionName,
            creatorId = creatorId,
            staffIds = mutableSetOf(),
            emailNotificationsEnabled = false,
            emailTemplate = null,
            promoCodes = emptyList(),
            startDate = Instant.now(),
            schedulePublished = false,
            bracketsPublished = false,
            endDate = Instant.now(),
            timeZone = ZoneId.systemDefault().id,
            registrationInfo = RegistrationInfo(competitionId, false, mutableSetOf()),
            creationTimestamp = System.currentTimeMillis()
    )

}