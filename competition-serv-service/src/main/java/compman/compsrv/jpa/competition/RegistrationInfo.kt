package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.RegistrationGroupDTO
import compman.compsrv.model.dto.competition.RegistrationInfoDTO
import compman.compsrv.model.dto.competition.RegistrationPeriodDTO
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "registration_period")
class RegistrationPeriod(@Id var id: String,
                         var name: String,
                         @ManyToOne
                         @JoinColumn(name = "registration_info_id", nullable = false)
                         var registrationInfo: RegistrationInfo?,
                         var start: Instant,
                         var end: Instant,
                         @OrderColumn
                         @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY, cascade = [CascadeType.ALL], targetEntity = RegistrationGroup::class, mappedBy = "registrationPeriod")
                         var registrationGroups: MutableList<RegistrationGroup> = mutableListOf()) {
    companion object {
        fun fromDTO(dto: RegistrationPeriodDTO) = RegistrationPeriod(dto.id, dto.name, null, dto.start, dto.end, dto.registrationGroups?.map { RegistrationGroup.fromDTO(it) }?.toMutableList()
                ?: mutableListOf())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegistrationPeriod

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int = 31
    fun toDTO(): RegistrationPeriodDTO = RegistrationPeriodDTO()
            .setId(id)
            .setCompetitionId(registrationInfo?.id)
            .setName(name)
            .setEnd(end)
            .setStart(start)
            .setRegistrationGroups(registrationGroups.map { it.toDTO() }.toTypedArray())
}

@Entity
class RegistrationGroup(@Id val id: String,
                        @ManyToOne
                        @JoinColumn(name = "registration_period_id", nullable = false)
                        var registrationPeriod: RegistrationPeriod?,
                        var displayName: String,
                        var registrationFee: BigDecimal,
                        var categories: Array<String>) {
    companion object {
        fun fromDTO(dto: RegistrationGroupDTO) = RegistrationGroup(dto.id, null, dto.displayName, dto.registrationFee, dto.categories ?: emptyArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegistrationGroup

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int = 31
    fun toDTO(): RegistrationGroupDTO = RegistrationGroupDTO()
            .setRegistrationPeriodId(registrationPeriod?.id)
            .setDisplayName(displayName)
            .setId(id)
            .setRegistrationFee(registrationFee)

}

@Entity
@Table(name = "registration_info")
class RegistrationInfo(id: String,
                       var registrationOpen: Boolean,
                       @OrderColumn
                       @OneToMany(orphanRemoval = true, mappedBy = "registrationInfo", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], targetEntity = RegistrationPeriod::class)
                       var registrationPeriods: MutableList<RegistrationPeriod> = mutableListOf()) : AbstractJpaPersistable<String>(id) {
    fun toDTO(): RegistrationInfoDTO = RegistrationInfoDTO()
            .setId(id)
            .setRegistrationOpen(registrationOpen)
            .setRegistrationPeriods(registrationPeriods.map { it.toDTO() }.toTypedArray())

    companion object {
        fun fromDTO(dto: RegistrationInfoDTO) = RegistrationInfo(dto.id, dto.registrationOpen, dto.registrationPeriods?.map { RegistrationPeriod.fromDTO(it) }?.toMutableList()
                ?: mutableListOf())
    }
}