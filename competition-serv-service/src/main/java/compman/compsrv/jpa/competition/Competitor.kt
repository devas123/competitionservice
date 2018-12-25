package compman.compsrv.jpa.competition

import com.compmanager.model.payment.RegistrationStatus
import compman.compsrv.model.dto.competition.CompetitorDTO
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id


@Entity
data class Competitor(
        @Id val id: String,
        val email: String,
        val userId: String?,
        val firstName: String,
        val lastName: String,
        val birthDate: Date?,
        val academy: String?,
        @Column(name = "category_id",
                columnDefinition = "VARCHAR(255) REFERENCES category_descriptor(id)")
        val categoryId: String,
        @Column(name = "competition_id",
                columnDefinition = "VARCHAR(255) REFERENCES competition_properties(id)")
        val competitionId: String,
        val registrationStatus: RegistrationStatus?,
        val promo: String?) {

    override fun equals(other: Any?) = if (other is Competitor) {
        id == other.id
    } else {
        false
    }

    override fun hashCode() = id.hashCode()
    fun toDTO(): CompetitorDTO? {
        return CompetitorDTO()
                .setId(id)
                .setAcademy(academy)
                .setBirthDate(birthDate)
                .setCategoryId(categoryId)
                .setCompetitionId(competitionId)
                .setEmail(email)
                .setFirstName(firstName)
                .setLastName(lastName)
                .setPromo(promo)
                .setRegistrationStatus(registrationStatus?.name)
                .setUserId(userId)
    }

    companion object {
        fun fromDTO(dto: CompetitorDTO) =
                Competitor(
                        id = dto.id,
                        email = dto.email,
                        userId = dto.userId,
                        firstName = dto.firstName,
                        lastName = dto.lastName,
                        birthDate = dto.birthDate,
                        academy = dto.academy,
                        categoryId = dto.categoryId,
                        competitionId = dto.competitionId,
                        registrationStatus = RegistrationStatus.valueOf(dto.registrationStatus),
                        promo = dto.promo
                )
    }
}