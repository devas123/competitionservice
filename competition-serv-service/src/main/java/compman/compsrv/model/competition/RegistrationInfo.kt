package compman.compsrv.model.competition

import java.math.BigDecimal
import java.time.ZonedDateTime
import javax.persistence.*

@Entity
data class RegistrationPeriod(@Id @GeneratedValue val id: Long,
                              val start: ZonedDateTime,
                              val end: ZonedDateTime,
                              @OrderColumn
                              @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
                              @JoinColumn(name = "registration_period")
                              val registrationGroups: Array<RegistrationGroup>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegistrationPeriod

        if (id != other.id) return false
        if (start != other.start) return false
        if (end != other.end) return false

        return true
    }

    override fun hashCode(): Int = 31
}

@Entity
data class RegistrationGroup(@Id @GeneratedValue(strategy = GenerationType.SEQUENCE) val id: Long? = null, val displayName: String, val registrationFee: BigDecimal)

@Entity
data class RegistrationInfo(@Id val id: String,
                            @OrderColumn
                            @OneToMany(orphanRemoval = true)
                            @JoinColumn(name = "registrationInfoId")
                            val registrationPeriods: Array<RegistrationPeriod>) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegistrationInfo

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return 31
    }
}