package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.RegistrationGroupDTO
import compman.compsrv.model.dto.competition.RegistrationInfoDTO
import compman.compsrv.model.dto.competition.RegistrationPeriodDTO
import java.math.BigDecimal
import java.time.ZonedDateTime
import javax.persistence.*

@Entity
class RegistrationPeriod(@Id @GeneratedValue(strategy = GenerationType.SEQUENCE) var id: Long?,
                         var name: String,
                         var start: ZonedDateTime,
                         var end: ZonedDateTime,
                         @OrderColumn
                         @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
                         @JoinColumn(name = "registration_period")
                         var registrationGroups: Array<RegistrationGroup>) {
    companion object {
        fun fromDTO(dto: RegistrationPeriodDTO) = RegistrationPeriod(dto.id, dto.name, dto.start, dto.end, dto.registrationGroups.map { RegistrationGroup.fromDTO(it) }.toTypedArray())
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
            .setName(name)
            .setEnd(end)
            .setStart(start)
            .setRegistrationGroups(registrationGroups.map { it.toDTO() }.toTypedArray())
}

@Entity
class RegistrationGroup(@Id @GeneratedValue(strategy = GenerationType.SEQUENCE) val id: Long? = null,
                        val displayName: String,
                        val registrationFee: BigDecimal) {
    companion object {
        fun fromDTO(dto: RegistrationGroupDTO) = RegistrationGroup(dto.id, dto.displayName, dto.registrationFee)
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
            .setDisplayName(displayName)
            .setId(id)
            .setRegistrationFee(registrationFee)

}

@Entity
class RegistrationInfo(id: String,
                       var registrationOpen: Boolean,
                       @OrderColumn
                       @OneToMany(orphanRemoval = true, fetch = FetchType.LAZY)
                       @JoinColumn(name = "registrationInfoId")
                       var registrationPeriods: Array<RegistrationPeriod>) : AbstractJpaPersistable<String>(id) {
    fun toDTO(): RegistrationInfoDTO = RegistrationInfoDTO()
            .setId(id)
            .setRegistrationOpen(registrationOpen)
            .setRegistrationPeriods(registrationPeriods.map { it.toDTO() }.toTypedArray())

    companion object {
        fun fromDTO(dto: RegistrationInfoDTO) = RegistrationInfo(dto.id, dto.registrationOpen, dto.registrationPeriods.map { RegistrationPeriod.fromDTO(it) }.toTypedArray())
    }
}