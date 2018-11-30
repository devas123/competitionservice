package compman.compsrv.model.competition

import com.compmanager.model.payment.RegistrationStatus
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id


@Entity
data class Competitor(
        @Id val id: String,
        val userId: String?,
        val firstName: String,
        val lastName: String,
        val birthDate: Date?,
        val academy: String?,
        @Column(name="category_id",
                columnDefinition = "VARCHAR(255) REFERENCES category_descriptor(id)")
        val categoryId: String,
        val competitionId: String,
        val registrationStatus: RegistrationStatus?,
        val promo: String?) {

    override fun equals(other: Any?) = if (other is Competitor) {
        id == other.id
    } else {
        false
    }

    override fun hashCode() = id.hashCode()
}