package compman.compsrv.jpa.competition

import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.jpa.AbstractJpaPersistable
import org.hibernate.annotations.Cascade
import org.hibernate.annotations.CascadeType
import java.time.Instant
import javax.persistence.*


@Entity
class Competitor(
        id: String,
        var email: String,
        var userId: String?,
        var firstName: String,
        var lastName: String,
        var birthDate: Instant?,
        @Embedded
        @AttributeOverrides(AttributeOverride(name = "id", column = Column(name = "academy_id")),
                AttributeOverride(name = "name", column = Column(name = "academy_name")))
        var academy: Academy?,
        @ManyToMany(fetch = FetchType.LAZY)
        @JoinColumn(name = "competitor_to_category")
        @Cascade(CascadeType.SAVE_UPDATE, CascadeType.MERGE, CascadeType.PERSIST)
        var categories: MutableSet<CategoryDescriptor>?,
        @Column(name = "competition_id",
                columnDefinition = "VARCHAR(255) REFERENCES competition_properties(id)")
        var competitionId: String,
        var registrationStatus: RegistrationStatus?,
        var promo: String?) : AbstractJpaPersistable<String>(id)