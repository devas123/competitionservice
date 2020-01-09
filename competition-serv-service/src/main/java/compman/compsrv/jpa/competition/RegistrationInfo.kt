package compman.compsrv.jpa.competition

import compman.compsrv.jpa.AbstractJpaPersistable
import compman.compsrv.model.dto.competition.RegistrationGroupDTO
import compman.compsrv.model.dto.competition.RegistrationInfoDTO
import compman.compsrv.model.dto.competition.RegistrationPeriodDTO
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "registration_period")
class RegistrationPeriod(@Id var id: String,
                         var name: String,
                         @ManyToOne(fetch = FetchType.LAZY)
                         @JoinColumn(name = "registration_info_id", nullable = false)
                         var registrationInfo: RegistrationInfo?,
                         var startDate: Instant,
                         var endDate: Instant,
                         @ManyToMany(fetch = FetchType.LAZY,
                                 targetEntity = RegistrationGroup::class)
                         @JoinTable(name = "reg_group_reg_period",
                                 joinColumns = [JoinColumn(name = "reg_group_id")],
                                 inverseJoinColumns = [JoinColumn(name = "reg_period_id")]
                         )
                         @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.PERSIST)
                         var registrationGroups: MutableSet<RegistrationGroup>? = mutableSetOf()) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegistrationPeriod

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int = 31
}

@Entity
class RegistrationGroup(@Id val id: String,
                        @ManyToOne(fetch = FetchType.LAZY)
                        @JoinColumn(name = "registration_info_id", nullable = false)
                        var registrationInfo: RegistrationInfo?,
                        var defaultGroup: Boolean,
                        @ManyToMany(fetch = FetchType.LAZY, mappedBy = "registrationGroups")
                        var registrationPeriods: MutableSet<RegistrationPeriod>?,
                        var displayName: String,
                        var registrationFee: BigDecimal,
                        @ElementCollection
                        @OrderColumn
                        var categories: MutableSet<String>?) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegistrationGroup

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int = 31
}

@Entity
@Table(name = "registration_info")
class RegistrationInfo(id: String,
                       var registrationOpen: Boolean,
                       @OneToMany(orphanRemoval = true, mappedBy = "registrationInfo", fetch = FetchType.LAZY, targetEntity = RegistrationPeriod::class)
                       @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.PERSIST)
                       var registrationPeriods: MutableSet<RegistrationPeriod> = mutableSetOf(),
                       @OneToMany(orphanRemoval = true, mappedBy = "registrationInfo", fetch = FetchType.LAZY, targetEntity = RegistrationGroup::class)
                       @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.PERSIST)
                       var registrationGroups: MutableSet<RegistrationGroup> = mutableSetOf()) : AbstractJpaPersistable<String>(id) {
    constructor() : this("", false, mutableSetOf())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegistrationInfo

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int = 31


}